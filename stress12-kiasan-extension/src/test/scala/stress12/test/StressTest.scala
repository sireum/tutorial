package stress12.test

import java.io._
import org.scalatest.junit._
import org.sireum.kiasan.state._
import org.sireum.pilar.ast._
import org.sireum.pilar.state._
import org.sireum.test.framework._
import org.sireum.topi._
import org.sireum.util._
import stress12._

trait StressTest[S <: KiasanStatePart[S]] extends TestFramework {
  type V = Value

  import language.implicitConversions
  
  case class ExpEvalResult(state : S, value : V)
  implicit def re2result(p : (S, V)) = ExpEvalResult(p._1, p._2)
  implicit def int2ci(n : Int) = CI(n)

  case class IsMatcher(o : Any) {
    def is(other : Any) {
      (o, other) match {
        case (v : V, n : Int)              => v should equal(CI(n))
        case (pcs : Seq[_], s : String)  => pcs should equal(pc(s))
        case (pcs : Seq[_], s : Product) => pcs should equal(pc(toSeq(s) : _*))
        case _                             => o should equal(other)
      }
    }
  }

  def toSeq(p : Product) : Seq[String] = {
    var l = ilistEmpty[String]
    for (o <- p.productIterator) {
      l = o.toString :: l
    }
    l.reverse
  }

  implicit def any2is(any : Any) = IsMatcher(any)

  def rewriter : PilarAstNode => PilarAstNode

  def pc(exp : String*) : Seq[Exp] = exp.map { source =>
    import org.sireum.test.framework.TestUtil._

    val (eOpt, errors) = parse(Left(source), classOf[Exp])

    assert(errors == "", "Expecting no parse error, but found:\n" + errors)

    rewriter(eOpt.get).asInstanceOf[Exp]
  }

  def check(s : S) = topi(_.check(s.pathConditions.reverse))
  
  def topi[T](f : Topi => T) = {
    val topi = createTopi
    try f(topi) finally topi.close
  } 

  def getModel(s : S) = {
    val m = topi(_.getModel(s.pathConditions.reverse))
    val result = mmapEmpty[Value, Value]
    for ((k, v) <- m.get) {
      result(KI(k.substring(2).toInt)) = CI(v.asInstanceOf[Topi.Integer].value.toInt)
    }
    result.toMap
  }

  def translatePathConditionsToZ3(s : S) = {
    val et = MyIntExtension.z3BackEndPart.expTranslator
    var tc = imapEmpty[ResourceUri, Int]
    val sb = new StringBuffer
    for (pc <- s.pathConditions.reverse) {
      val (tc2, s) = et((tc, pc))
      tc = tc2
      sb.append(s)
    }
    sb.toString
  }

  def createTopi = Topi.create(TopiSolver.Z3, TopiMode.Process, 10000, MyIntExtension)
}