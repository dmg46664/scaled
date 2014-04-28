//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled

import java.io.File

import reactual.{SignalV, ValueV}

import scala.annotation.tailrec

/** A location in a buffer which responds as predictably as possible to changes in the buffer.
  * Edits that precede the anchor cause it to shift forward or back appropriately. Edits after the
  * anchor do not cause movement. Deleting the text that includes the anchor causes it to move to
  * the start of the deleted range.
  */
trait Anchor {

  /** Returns this anchor's current location. */
  def loc :Loc
}

/** Manages a sequence of characters, providing a line-by-line view and the means to translate
  * between character offset and line offset plus (intra-line) character offset. This isolates the
  * read-only API, see [[Buffer]] for the mutable API and [[RBuffer]] for the reactive-mutable API.
  *
  * Note: there's only one implementation of buffers, but the API factorization exists to allow
  * differing degrees of control to be exposed depending on the circumstances. If someone should
  * not be allowed to mutate a buffer, they can be supplied with a `BufferV` reference. If they can
  * mutate, but should not be allowed to register (possibly memory leak-inducing) reactive
  * handlers, they can be supplied with a `Buffer` reference. If they should have all the games and
  * all the puzzles, they get an `RBuffer` reference.
  *
  * For better and worse, the JVM allows you to downcast to the more powerful type. That's a bad
  * idea. Don't do it. Consider yourself warned.
  *
  * @define RNLNOTE Note: the last line does not conceptually include a trailing newline, and
  * [[insert(Loc,Seq[LineV])]] takes this into account.
  */
abstract class BufferV extends Region {

  /** The name of this buffer. Tends to be the name of the file from which it was read, but may
    * differ if two buffers exist with the same file name. */
  def name :String

  /** The file being edited by this buffer. */
  def file :File

  /** The directory that contains the file being edited by this buffer. */
  def dir :File = if (file.isDirectory) file else file.getParentFile

  /** The current mark, if any. */
  def mark :Option[Loc]

  /** Whether or not this buffer has been modified since it was loaded or last saved. */
  def dirty :Boolean

  /** Returns the position at the start of the buffer. This is always [[Loc.Zero]], but this method
    * exists for symmetry with [[end]]. */
  def start :Loc = Loc.Zero

  /** Returns the position at the end of the buffer. This will be one character past the end of the
    * last line in the buffer. */
  def end :Loc = Loc(lines.size-1, lines.last.length)

  /** Returns a location for the specified character offset into the buffer. If `offset` is greater
    * than the length of the buffer, the returned `Loc` will be positioned after the buffer's final
    * character. */
  def loc (offset :Int) :Loc

  /** Returns the character offset into the buffer of `loc`. */
  def offset (loc :Loc) :Int

  /** A read-only view of the lines in this buffer. */
  def lines :Seq[LineV]

  /** Returns the `idx`th line. Indices are zero based.
    * @throws IndexOutOfBoundsException if `idx` is not a valid line index. */
  def line (idx :Int) :LineV = lines(idx)

  /** Returns the line referenced by `loc`.
    * @throws IndexOutOfBoundsException if `loc.row` is not a valid line index. */
  def line (loc :Loc) :LineV = line(loc.row)

  /** Returns the length of the line at `idx`, or zero if `idx` represents a line beyond the end of
    * the buffer or before its start. */
  def lineLength (idx :Int) :Int = if (idx < 0 || idx >= lines.length) 0 else lines(idx).length

  /** Returns the length of the line at `loc`, or zero if `loc` represents a line beyond the end of
    * the buffer or before its start. */
  def lineLength (loc :Loc) :Int = lineLength(loc.row)

  /** Returns the length of the longest line in this buffer. */
  def maxLineLength :Int

  /** Returns the character at `loc`.
    * @throws IndexOutOfBoundsException if `loc.row` is not a valid line index. */
  def charAt (loc :Loc) :Char = line(loc.row).charAt(loc.col)

  /** Returns the CSS style classes of the character at `loc`.
    * @throws IndexOutOfBoundsException if `loc.row` is not a valid line index. */
  def stylesAt (loc :Loc) :Styles = line(loc.row).stylesAt(loc.col)

  /** Returns a copy of the data between `[start, until)`. $RNLNOTE */
  def region (start :Loc, until :Loc) :Seq[Line]

  /** Returns a copy of the data in region `r`. $RNLNOTE */
  def region (r :Region) :Seq[Line] = region(r.start, r.end)

  /** Returns the start of the line at `row`. */
  def lineStart (row :Int) :Loc = Loc(row, 0)

  /** Returns the start of the line at `loc.row`. */
  def lineStart (loc :Loc) :Loc = loc.atCol(0)

  /** Returns the end of the line at `row`. */
  def lineEnd (row :Int) :Loc = Loc(row, lineLength(row))

  /** Returns the end of the line at `loc.row`. */
  def lineEnd (loc :Loc) :Loc = loc.atCol(lineLength(loc.row))

  /** Bounds `loc` into this buffer. Its row will be bound to [0, `lines.length`) and its column
    * bound into the line to which its row was bound. */
  def bound (loc :Loc) :Loc = {
    if (loc.row >= lines.size) Loc(lines.size-1, lines.last.length)
    else if (loc.row < 0) Loc(0, lines(0).bound(loc.col))
    else lines(loc.row).bound(loc)
  }

  /** Returns the loc `count` characters forward of `loc`, or [[end]] if we reach it first. */
  def forward (loc :Loc, count :Int) :Loc = {
    @inline @tailrec def seek (row :Int, col :Int, remain :Int) :Loc = {
      val lcol = col + remain
      val llen = lines(row).length
      if (llen >= lcol) Loc(row, lcol)
      else if (row == lines.length-1) end
      else seek(row+1, 0, lcol-llen-1) // -1 to account for line separator
    }
    seek(loc.row, loc.col, count)
  }

  /** Returns the loc `count` characters backward of `loc`, or [[start]] if we reach it first. */
  def backward (loc :Loc, count :Int) :Loc = {
    @inline @tailrec def seek (row :Int, col :Int, remain :Int) :Loc = {
      val lcol = col - remain
      if (lcol >= 0) Loc(row, lcol)
      else if (row == 0) start
      else seek(row-1, line(row-1).length, remain-col-1) // -1 to account for line separator
    }
    seek(loc.row, loc.col, count)
  }

  /** Scans forward from `start` for a character that matches `pred`. If `start` matches, it will be
    * returned. If `stop` is reached before finding a match, `stop` is returned. Note that end of
    * line characters are included in the scan.
    * @param pred a predicate that will be passed `(row, col, char)`.
    */
  def scanForward (pred :(Int,Int,Char) => Boolean, start :Loc, stop :Loc = this.end) :Loc = {
    val stopr = stop.row ; val stopc = stop.col
    @inline @tailrec def seek (row :Int, col :Int) :Loc = {
      val line = this.line(row)
      val last = if (row == stopr) stopc else line.length
      var p = col ; while (p <= last && !pred(row, p, line.charAt(p))) p += 1
      if (p <= last) Loc(row, p)
      else if (row == stopr) stop
      else seek(row+1, 0)
    }
    if (start < stop) seek(start.row, start.col) else stop
  }

  /** Scans backward from the location immediately previous to `start` for a character that matches
    * `pred`. If `stop` is reached before finding a match, `stop` is returned. Note that end of
    * line characters are included in the scan.
    * @param pred a predicate that will be passed `(row, col, char)`.
    */
  def scanBackward (pred :(Int,Int,Char) => Boolean, start :Loc, stop :Loc = this.start) :Loc = {
    val stopr = stop.row ; val stopc = stop.col
    @inline @tailrec def seek (row :Int, col :Int) :Loc = {
      val line = this.line(row)
      val first = if (row == stopr) stopc else 0
      var p = col ; while (p >= first && !pred(row, p, line.charAt(p))) p -= 1
      if (p >= first) Loc(row, p)
      else if (row == stopr) stop
      else seek(row-1, this.line(row-1).length)
    }
    if (stop < start) seek(start.row, start.col-1) else stop
  }

  /** Searches forward from `start` for the first match of `m`, stopping before `stop` (`stop` is
    * not checked for a match).
    * @return the location of the match or `Loc.None`. */
  def findForward (m :Matcher, start :Loc, stop :Loc = this.end) :Loc = {
    val stopr = stop.row ; val stopc = stop.col
    @inline @tailrec def seek (row :Int, col :Int) :Loc = line(row).indexOf(m, col) match {
      case -1 => if (row == stopr) Loc.None else seek(row+1, 0)
      case ii => if (row == stopr && ii >= stopc) Loc.None else Loc(row, ii)
    }
    if (start < stop) seek(start.row, start.col) else Loc.None
  }

  /** Searches backward from the location immediately previous to `start` for the first match of
    * `m`, stopping when `stop` is reached (`stop` is checked for a match).
    * @return the location of the match or `Loc.None`. */
  def findBackward (m :Matcher, start :Loc, stop :Loc = this.start) :Loc = {
    val stopr = stop.row ; val stopc = stop.col
    @inline @tailrec def seek (row :Int, col :Int) :Loc = line(row).lastIndexOf(m, col) match {
      case -1 => if (row == stopr) Loc.None else seek(row-1, line(row-1).length)
      case ii => if (row == stopr && ii < stopc) Loc.None else Loc(row, ii)
    }
    if (start > stop) seek(start.row, start.col-1) else Loc.None
  }

  /** Searches for `lns` in the buffer, starting at `start` and not searching beyond `end`. If only
    * a single line is sought, it can match anywhere in a line in the buffer. If more than one line
    * is sought, the first line must match the end of a buffer line, and subsequent lines must
    * match entirely until the last line which must match the start of the corresponding buffer
    * line. The text of the lines must not contain line separator characters.
    *
    * If the sought text is mixed case, exact-case matching is used, otherwise case-insensitive
    * matching is used.
    *
    * @param maxMatches the search is stopped once this many matches are found.
    */
  def search (lns :Seq[LineV], start :Loc, end :Loc, maxMatches :Int = Int.MaxValue) :Seq[Loc] = {
    lns match {
      case Seq() => Seq()
      // searching for a single line can match anywhere in a line, so we handle specially
      case Seq(ln) => if (ln.length == 0) Seq()
                      else searchFrom(Matcher.on(ln), start, end, maxMatches, Seq())
      // multiline searches have to match the end of one line, zero or more intermediate lines, and
      // the start of the final line
      case lines =>
        Seq() // TODO
    }
  }

  private def searchFrom (m :Matcher, start :Loc, end :Loc, maxMatches :Int,
                          accum :Seq[Loc]) :Seq[Loc] =
    if (maxMatches == 0 || start > end) accum
    else line(start).indexOf(m, start.col) match {
      // if no match on this line, move to the next line and keep searching
      case -1 => searchFrom(m, start.nextStart, end, maxMatches, accum)
      // if we did match on this line, make sure it's < end, add it, and keep searching
      case ii =>
        val next = start.atCol(ii+m.matchLength)
        if (next > end) accum
        else searchFrom(m, next, end, maxMatches-1, accum :+ (start atCol ii))
    }
}

/** Extends [[BufferV]] with a mutation API. See [[RBuffer]] for a further extension which provides
  * the ability to register to react to changes.
  */
abstract class Buffer extends BufferV {

  /** Saves this buffer to its current file. If the buffer is not dirty, NOOPs. */
  def save () {
    // the caller should check dirty and provide feedback, but let's nip funny biz in the bud
    if (dirty) saveTo(file)
  }

  /** Saves this buffer to `file`, updating [[file]] and [[name]] appropriately. */
  def saveTo (file :File) :Unit

  /** Sets the current mark to `loc`. The mark will be [[bound]] into the buffer. */
  def mark_= (loc :Loc) :Unit
  /** Clears the current mark. */
  def clearMark () :Unit

  /** That which handles undoing and redoing for this buffer. */
  def undoer :Undoer

  /** Inserts the single character `c` into this buffer at `loc` with CSS style classes `styles`. */
  def insert (loc :Loc, c :Char, style :Styles) :Unit

  /** Inserts the raw string `s` into this buffer at `loc` with CSS style class `style`. */
  def insert (loc :Loc, s :String, styles :Styles) {
    if (s.length == 1) insert(loc, s.charAt(0), styles)
    else insert(loc, new Line(s, styles))
  }

  /** Inserts the contents of `line` into this buffer at `loc`. The line in question will be spliced
    * into the line at `loc`, a new line will not be created. If you wish to create a new line,
    * [[split]] at `loc` and then insert into the appropriate half.
    *
    * @return the buffer location just after the inserted line. */
  def insert (loc :Loc, line :LineV) :Loc

  /** Inserts `region` into this buffer at `loc`. `region` will often have come from a call to
    * [[region(Loc,Loc)]] or [[delete(Loc,Loc)]].
    *
    * The lines will be spliced into the line at `loc` which is almost certainly what you want.
    * This means the line at `loc` will be [[split]] in two, the first line in `region` will be
    * appended to the first half of the split line, and the last line `region` will be prepended to
    * the last half of the split line; the lines in between (if any) are inserted as is. If
    * `region` is length 1 this has the same effect as [[insert(Loc,Line)]].
    *
    * @return the buffer location just after the end of the inserted region.
    */
  def insert (loc :Loc, region :Seq[LineV]) :Loc

  /** Deletes `count` characters from the line at `loc`.
    * @return the deleted chars as a line. */
  def delete (loc :Loc, count :Int) :Line
  /** Deletes the data between `[start, until)` from the buffer. Returns a copy of the deleted data.
    * $RNLNOTE */
  def delete (start :Loc, until :Loc) :Seq[Line]
  /** Deletes the data in region `r` from the buffer. Returns a copy of the deleted data. $RNLNOTE */
  def delete (r :Region) :Seq[Line] = delete(r.start, r.end)

  /** Replaces `count` characters in the line at `loc` with the `line`.
    * @return the replaced characters as a line. */
  def replace (loc :Loc, count :Int, line :LineV) :Line
  /** Replaces the region between `[start, until)` with `lines`.
    * @return the buffer location just after the replaced region. */
  def replace (start :Loc, until :Loc, lines :Seq[LineV]) :Loc
  /** Replaces the region `r` with `lines`.
    * @return the buffer location just after the replaced region. */
  def replace (r :Region, lines :Seq[LineV]) :Loc = replace(r.start, r.end, lines)

  /** Transforms the characters between `[start, until)` using `fn`.
    * @return the buffer location just after the transformed region. */
  def transform (start :Loc, until :Loc, fn :Char => Char) :Loc
  /** Transforms the characters in region `r` using `fn`.
    * @return the buffer location just after the transformed region. */
  def transform (r :Region, fn :Char => Char) :Loc = transform(r.start, r.end, fn)

  /** Splits the line at `loc`. The characters up to `loc.col` will remain on the `loc.row`th line,
    * and the character at `loc.col` and all subsequent characters will be moved to a new line
    * which immediately follows the `loc.row`th line. */
  def split (loc :Loc) :Unit

  /** Updates the CSS style classes of the characters between `[start, until)` by applying `fn` to
    * the existing styles. To add a single style do: `updateStyles(_ + style, ...)` to remove a
    * single style do: `updateStyles(_ - style, ...)`. */
  def updateStyles (fn :Styles => Styles, start :Loc, until :Loc) :Unit
  /** Updates the CSS style classes of the characters in region `r` by applying `fn` to the existing
    * styles. */
  def updateStyles (fn :Styles => Styles, r :Region) :Unit = updateStyles(fn, r.start, r.end)

  /** Adds CSS style class `style` to the characters between `[start, until)`. */
  def addStyle (style :String, start :Loc, until :Loc) = updateStyles(_ + style, start, until)
  /** Adds CSS style class `style` to the characters in region `r`. */
  def addStyle (style :String, r :Region) :Unit = addStyle(style :String, r.start, r.end)
  /** Removes CSS style class `style` from the characters between `[start, until)`. */
  def removeStyle (style :String, start :Loc, until :Loc) = updateStyles(_ - style, start, until)
  /** Removes CSS style class `style` from the characters in region `r`. */
  def removeStyle (style :String, r :Region) :Unit = removeStyle(style, r.start, r.end)
}

/** `Buffer` related types and utilities. */
object Buffer {

  /** Conveys information about a buffer edit. See [[Insert]], [[Delete]], [[Transform]]. */
  sealed trait Edit extends Region with Undoable

  /** An event emitted when text is inserted into a buffer. */
  class Insert (val start :Loc, val end :Loc, buffer :Buffer) extends Edit {
    def undo () = buffer.delete(start, end)
    def merge (other :Insert) :Insert = {
      assert(end == other.start, "Cannot merge non-adjacent $this and $other")
      new Insert(start, other.end, buffer)
    }
    override def toString = s"Edit[+${Region.toString(this)}]"
  }
  object Insert {
    def unapply (edit :Insert) = Some((edit.start, edit.end))
  }

  /** An event emitted when text is deleted from a buffer. */
  class Delete (val start :Loc, val deletedRegion :Seq[Line], buffer :Buffer) extends Edit {
    val end :Loc = start + deletedRegion
    def undo () = buffer.insert(start, deletedRegion)
    override def toString = s"Edit[-${Region.toString(this)}]"
  }
  object Delete {
    def unapply (edit :Delete) = Some((edit.start, edit.end, edit.deletedRegion))
  }

  /** An event emitted when text in a buffer is transformed. */
  class Transform (val start :Loc, val original :Seq[Line], buffer :Buffer) extends Edit {
    val end :Loc = start + original
    def undo () = buffer.replace(start, end, original)
    override def toString = s"Edit[!${Region.toString(this)}]"
  }
  object Transform {
    def unapply (edit :Transform) = Some((edit.start, edit.end, edit.original))
  }
}

/** The reactive version of [Buffer]. */
abstract class RBuffer extends Buffer {

  /** A reactive view of [[name]]. */
  def nameV :ValueV[String]

  /** A reactive view of [[file]]. */
  def fileV :ValueV[File]

  /** The current mark, if any. */
  def markV :ValueV[Option[Loc]]

  /** A reactive view of [[dirty]]. */
  def dirtyV :ValueV[Boolean]

  /** A signal emitted when this buffer is edited. */
  def edited :SignalV[Buffer.Edit]

  /** A signal emitted when a line in this buffer has a CSS style applied to it. The emitted `Loc`
    * will contain the row of the line that was edited and the positon of the earliest character to
    * which a style was applied. Zero or more additional style changes may have been made to
    * characters after the one identified by the `Loc`, but none will have been made to characters
    * before that `Loc`. */
  def lineStyled :SignalV[Loc]

  // implement some Buffer methods in terms of our reactive values
  override def name = nameV()
  override def file = fileV()
  override def mark = markV()
  override def dirty = dirtyV()
}
