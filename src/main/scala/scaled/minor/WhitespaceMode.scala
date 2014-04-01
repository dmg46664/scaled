//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled.minor

import scala.annotation.tailrec
import scala.collection.mutable.{Set => MSet}

import scaled._
import scaled.major.{EditingMode, Syntax}
import scaled.util.Behavior

object WhitespaceConfig extends ConfigDefs {

  val showTrailingWhitespace = key(
    "If true, trailing whitespace will be highlighted", true)

  /** The CSS style applied to trailing whitespace characters. */
  val trailingWhitespaceStyle = "trailingWhitespaceFace"
}

@Minor(name="whitespace", desc="""
       A minor mode that provides whitespace manipulation fns and can highlight undesirable
       whitespace.""")
class WhitespaceMode (editor :Editor, config :Config, view :RBufferView, major :EditingMode)
    extends MinorMode {
  import WhitespaceConfig._

  val trailingWhitespacer = new Behavior() {
    private val _rethinkLines = MSet[Int]()
    private var _lastPoint = view.point()

    override protected def activate () {
      // respond to buffer and line edits
      note(view.buffer.edited onValue { edit =>
        queueRethink(edit.offset until (edit.offset+edit.added) :_*)
      })
      note(view.buffer.lineEdited onValue { edit =>
        if (edit.added > 0) queueRethink(edit.loc.row)
      })
      // when the point moves, the line it left may now need highlighting and the line it moves to
      // may no longer need highlighting
      note(view.point onValue { point => queueRethink(_lastPoint.row, point.row) })
      // note existing trailing whitespace
      0 until view.buffer.lines.size foreach tagTrailingWhitespace
      // TODO: defer marking trailing whitespace on non-visible lines until they're scrolled into
      // view, we can probably do this entirely in client code using RBufferView.scrollTop and
      // RBufferView.heightV; encapsulate it in a Colorizer helper class?
    }

    override protected def didDeactivate () {
      view.buffer.removeStyle(trailingWhitespaceStyle, view.buffer.start, view.buffer.end)
    }

    private def queueRethink (row :Int*) {
      val takeAction = _rethinkLines.isEmpty
      _rethinkLines ++= row
      if (takeAction) editor defer rethink
    }

    private def rethink () {
      _rethinkLines foreach tagTrailingWhitespace
      _rethinkLines.clear()
    }

    private val tagTrailingWhitespace = (ii :Int) => {
      val line = view.buffer.lines(ii)
      val limit = if (view.point().row == ii) view.point().col else 0
      @tailrec def seek (col :Int) :Int = {
        if (col == limit || major.syntax(line.charAt(col-1)) != Syntax.Whitespace) col
        else seek(col-1)
      }
      val last = line.length
      val first = seek(last)
      val floc = Loc(ii, first)
      if (first > 0) view.buffer.removeStyle(trailingWhitespaceStyle, Loc(ii, 0), floc)
      if (first < last) view.buffer.addStyle(trailingWhitespaceStyle, floc, Loc(ii, last))
    }
  }
  config.value(showTrailingWhitespace) onValueNotify trailingWhitespacer.setActive

  override def keymap = Seq() // TODO
  override def dispose () {
    trailingWhitespacer.setActive(false)
  }
}