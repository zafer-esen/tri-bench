import java.io.PrintWriter

import tricera.{Main => tri}
import tricera.benchmarking.Benchmarking._
import com.sun.jna.NativeLibrary

import scala.collection.mutable.ArrayBuffer

object Main extends App {
  NativeLibrary.addSearchPath("tricera-preprocessor", "lib")

  import java.io.File

  // exclude any subdir containing these names
  val excludeMasks = List(
    "array",       // arrays
    "Juliet_Test"  // 18140 benchmarks of same style, so if one fails all fail
  )
  val excludedDirs = new ArrayBuffer[File]
  val includedDirs = new ArrayBuffer[File]

  def getListOfFiles(dir: String, extensions: List[String]): List[File] = {
    val d = new File(dir)
    getListOfFiles(d, extensions)
  }
  def getListOfFiles(d: File, extensions: List[String]): List[File] = {
    if (d.isDirectory &&
        excludeMasks.exists(s=> d.getName.contains(s))) {
      excludedDirs += d
      Nil
    }
    else if (d.exists && d.isDirectory) {
      includedDirs += d
      d.listFiles.filter(_.isFile).toList.filter { file =>
        extensions.exists(file.getName.endsWith(_))
      } ++ d.listFiles.filter(_.isDirectory).
        flatMap(subDir => getListOfFiles(subDir, extensions))
    }
    else Nil
  }

  // todo: instead parse ymls and get inputs?
  val files = getListOfFiles("benchmarks", List(".i", ".c"))

  println("Num. benchmarks found: " + files.length)
  println("-"*80)
  /*println("Included benchmark directories" )
  println
  includedDirs.foreach(println)
  println("-"*80)
  println("Excluded benchmark directories" )
  println
  excludedDirs.foreach(println)
  println("-"*80)
  */

  val timeout = 300 //seconds

  val triParams = List(
    "-abstract:off",
    "-t:" + timeout.toString
  )

  sealed abstract class BMList (name : String) {
    private val files : ArrayBuffer[File] = new ArrayBuffer[File]
    override def toString : String = name + "\n" + "-"*80 + "\n" +
                                        files.mkString("\n")
    def nameAndCountAsString(padUntil : Int) : String =
      name + " "*(padUntil-name.length) + ": " + files.length
    def += (l : File) : Unit = files += l
    def length : Int = files.length
    def size   : Int = files.size
  }
  case object heapUsingBms extends BMList("Heap Using BMs")
  case object timeoutBms extends BMList("Timeout BMs")
  case object timeoutHeapBms extends BMList("Timeout Heap BMs")
  case object unsoundBms extends BMList("Unsound BMs")
  case object unsoundHeapBms extends BMList("Unsound Heap BMs")
  case object incompleteBms extends BMList("Incomplete BMs")
  case object incompleteHeapBms extends BMList("Incomplete Heap BMs")
  case object matchingSafeBms extends BMList("Matching Safe BMs")
  case object matchingSafeHeapBms extends BMList("Matching Safe Heap BMs")
  case object matchingUnsafeBms extends BMList("Matching Unsafe BMs")
  case object matchingUnsafeHeapBms extends BMList("Matching Unsafe Heap BMs")

  case object arrayBms extends BMList("Array BMs")
  case object parseErrorBms extends BMList("Parse Error BMs")
  case object outOfMemoryBms extends BMList("Out of Memory BMs")
  case object stackOverflowBms extends BMList("Stack Overflow BMs")
  case object otherErrorBms extends BMList("Other Error BMs")

  val allBms: List[BMList] = List(heapUsingBms, timeoutBms, timeoutHeapBms,
    unsoundBms, unsoundHeapBms, incompleteBms, incompleteHeapBms,
    matchingSafeBms, matchingSafeHeapBms, matchingUnsafeBms, matchingUnsafeHeapBms,
    arrayBms, parseErrorBms, outOfMemoryBms, stackOverflowBms, otherErrorBms)

  val bmLogFile = new PrintWriter(new File("bmOutputs.txt" ))
  val t0 = System.nanoTime
  val results = for ((file, ind) <- files.zipWithIndex) yield {
    println(file)
    val t1 = System.nanoTime
    val res : ExecutionSummary =
      tri.doMain(Array(file.getAbsolutePath) ++ triParams, stoppingCond = false)
    val duration = (System.nanoTime - t1) / 1e9d
    val totalDuration = (System.nanoTime - t0) / 1e9d
    if(res.modelledHeap)
      heapUsingBms += file

    val matched = res.executionResult match {
      case ParseError =>         parseErrorBms += file; false
      case ArrayError =>              arrayBms += file; false
      case OutOfMemory =>       outOfMemoryBms += file; false
      case StackOverflow =>   stackOverflowBms += file; false
      case _ : OtherError =>     otherErrorBms += file; false
      case Timeout =>               timeoutBms += file
        if(res.modelledHeap)    timeoutHeapBms += file; false
      case Safe =>
        if (res.trackResult.forall(track => track._2)) {
                                   matchingSafeBms += file;
          if(res.modelledHeap) matchingSafeHeapBms += file; true
        }
        else {
                                   incompleteBms += file;
          if(res.modelledHeap) incompleteHeapBms += file; false
        }
      case Unsafe =>
        res.trackResult.find(track => !track._2) match {
          case Some(t) =>
                                     matchingUnsafeBms += file
            if(res.modelledHeap) matchingUnsafeHeapBms += file; true
          case None =>
                                     unsoundBms += file
            if(res.modelledHeap) unsoundHeapBms += file; false
        }
      case _ => false// todo
    }

    bmLogFile.println("-"*80)
    bmLogFile.println("#" ++ (ind+1).toString ++ " " ++ file.getName)
    bmLogFile.println("Used heap        : " + res.modelledHeap)
    bmLogFile.println("Tracks           : " + res.trackResult.mkString(","))
    bmLogFile.println("Result           : " + res.executionResult)
    bmLogFile.println("Matches expected : " + matched)
    bmLogFile.println("Duration         : " + duration + " s")
    bmLogFile.println("Total Duration   : " + totalDuration + " s")
    bmLogFile.flush()

    val resultFile = new PrintWriter(new File("bmResults.txt" ))
    resultFile.println("="*80)
    for (bmList <- allBms) {
      resultFile.println(bmList.nameAndCountAsString(50))
    }
    resultFile.println("Total executed (not timed out) BMs : " +
      (unsoundBms.length + incompleteBms.length + matchingSafeBms.length +
       matchingUnsafeBms.length))
    resultFile.println("Total matching heap BMs : " +
      (matchingSafeHeapBms.length + matchingUnsafeBms.length))
    resultFile.println("Total unmatching heap BMs : " +
      (unsoundHeapBms.length + incompleteHeapBms.length))
    resultFile.println("Total error BMs (excluding arrays and timeouts) : " +
      (parseErrorBms.length + outOfMemoryBms.length +
       otherErrorBms.length + stackOverflowBms.length))
    resultFile.println("Total BMs (any) : " +
      (unsoundBms.length + incompleteBms.length + matchingSafeBms.length +
       matchingUnsafeBms.length + parseErrorBms.length + outOfMemoryBms.length +
       otherErrorBms.length + stackOverflowBms.length + arrayBms.length +
       timeoutBms.length))

    resultFile.println("="*80)
    resultFile.println("")
    resultFile.println("")
    for (bmList <- allBms) {
      resultFile.println("-"*80)
      resultFile.println(bmList)
      resultFile.println("-"*80)
    }
    resultFile.close()
    (file, res)
  }
  bmLogFile.close()


 /* val res : tri.ExecutionSummary = tri.doMain(Array(
      "a.c"
    ), stoppingCond = false
  )
  println(res.executionResult)
  res.trackResult.foreach(println)*/

}