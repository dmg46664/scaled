//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled.util

import org.junit.Assert._
import org.junit._
import scala.collection.mutable.ArrayBuffer
import scaled.Executor

class ExpecterTest {

  class AccumExec extends Executor {
    private val rs = ArrayBuffer[Runnable]()
    val uiExec = new java.util.concurrent.Executor() {
      override def execute (op :Runnable) :Unit = rs += op
    }
    val bgExec = uiExec
    def executeAll () = {
      rs foreach { _.run() }
      rs.clear()
    }
  }

  @Test def testUnexpected () {
    val exec = new AccumExec()
    val ex = new Expecter(exec, SubProcess.Config(Array("echo", "foo"))) {
      override def onUnexpected (line :String, isErr :Boolean) {
        assertEquals("foo", line)
      }
    }
    val rv = ex.waitFor()
    exec.executeAll()
    assertEquals(0, rv)
  }

  @Test def testOneLineInteraction () {
    val exec = new AccumExec()
    val ex = new Expecter(exec, SubProcess.Config(Array("cat"))) {
      override def onUnexpected (line :String, isErr :Boolean) {
        fail(s"Unexpected $line $isErr")
      }
    }
    ex.interact(Seq("hello")) { (line, isErr) =>
      assertEquals("hello", line)
      true
    }
    ex.close()
    assertEquals(0, ex.waitFor())
    exec.executeAll()
  }

  @Test def testMultilineInteraction () {
    val exec = new AccumExec()
    val ex = new Expecter(exec, SubProcess.Config(Array("cat"))) {
      override def onUnexpected (line :String, isErr :Boolean) {
        fail(s"Unexpected $line $isErr")
      }
    }
    ex.interact(Seq("howdy", "world")) { (line, isErr) =>
      if ("howdy" == line) false
      else {
        if ("world" != line) fail("Unexpected $line $isErr")
        true
      }
    }
    ex.close()
    assertEquals(0, ex.waitFor())
    exec.executeAll()
  }
}
