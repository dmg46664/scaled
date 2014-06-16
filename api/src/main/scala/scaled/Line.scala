//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled

import java.util.Arrays
import reactual.SignalV
import scala.annotation.tailrec

/** Models a single line of text, which may or may not be part of a buffer.
  *
  * Lines may be created externally from character or string data or they may be obtained as part
  * of a buffer. In the former case they are immutable and will have type [[Line]], in the latter
  * case they are a view on mutable data and have type [[LineV]]. Do not retain a reference to a
  * [[LineV]] as it may change after you relinquish control of the editor thread.
  */
abstract class LineV extends CharSequence {
  import scaled.util.Chars._

  /** The length (in characters) of this line. */
  def length :Int

  /** Returns the character at `pos`. If `pos == length` `\n` is returned. Otherwise if `pos` is
    * outside `[0,length]` 0 is returned. */
  def charAt (pos :Int) :Char = {
    val l = length
    if (pos >= 0 && pos < l) _chars(_offset+pos) else if (pos == l) '\n' else 0
  }

  /** Returns the CSS style classes applied to the character at `pos`, if any. */
  def stylesAt (pos :Int) :Styles = if (pos < length) _styles(_offset+pos) else Styles.None

  /** Returns the CSS style classes applied to the character at `pos`, if any. */
  def syntaxAt (pos :Int) :Syntax = if (pos < length) _syns(_offset+pos) else Syntax.Default

  /** Bounds the supplied column into this line. This adjusts it to be in [0, [[length]]] (inclusive
    * of the length because the point can be after the last char on this line). */
  def bound (col :Int) :Int = math.max(0, math.min(length, col))

  /** Bounds the supplied loc into this line by bounding its column via [[bound(Int)]]. */
  def bound (loc :Loc) :Loc = loc.atCol(bound(loc.col))

  /** Returns a view of the specified slice of this line. */
  def view (start :Int, until :Int = length) :LineV

  /** Extracts the specified region of this line into a new line.
    * @param start the index of the first character to include in the slice.
    * @param until one past the index of the last character to include in the slice. */
  def slice (start :Int, until :Int = length) :Line

  /** Copies `[start, until)` from this line into `cs`/`ss` at `offset`. */
  def sliceInto (start :Int, until :Int, cs :Array[Char], ss :Array[Styles], xs :Array[Syntax],
                 offset :Int) {
    System.arraycopy(_chars, _offset+start, cs, offset, until-start)
    System.arraycopy(_styles, _offset+start, ss, offset, until-start)
    System.arraycopy(_syns, _offset+start, xs, offset, until-start)
  }

  /** Returns the characters in `[start, until)` as a string. */
  def sliceString (start :Int, until :Int) :String = new String(_chars, _offset+start, until-start)

  /** Returns a new line which contains `other` appended to `this`. */
  def merge (other :LineV) :Line = {
    val cs = new Array[Char](length + other.length)
    val ss = new Array[Styles](cs.length)
    val xs = new Array[Syntax](cs.length)
    sliceInto(0, length, cs, ss, xs, 0)
    other.sliceInto(0, other.length, cs, ss, xs, length)
    new Line(cs, ss, xs)
  }

  /** Returns the index of the first occurrence of `ch` at pos `from` or later.
    * Returns -1 if `ch` is not found. */
  def indexOf (ch :Char, from :Int) :Int = {
    val offset = _offset ; val end = length
    var pos = from ; while (pos < end && _chars(offset+pos) != ch) pos += 1
    if (pos == end) -1 else pos
  }
  /** Returns the index of the first occurrence of `ch`. Returns -1 if `ch` is not found. */
  def indexOf (ch :Char) :Int = indexOf(ch, 0)

  /** Returns the index of the first occurrence of `ch` at pos `from` or earlier. Returns -1 if `ch`
    * is not found. */
  def lastIndexOf (ch :Char, from :Int) :Int = {
    val offset = _offset
    var pos = from ; while (pos >= 0 && _chars(offset+pos) != ch) pos -= 1
    pos
  }
  /** Returns the index of the first occurrence of `ch` seeking backward from the end of the line.
    * Returns -1 if `ch` is not found. */
  def lastIndexOf (ch :Char) :Int = lastIndexOf(ch, length-1)

  /** Returns the index of the first character that matches `pred` at pos `from` or later.
    * Returns -1 if no character matched. */
  def indexOf (pred :Char => Boolean, from :Int) :Int = {
    val offset = _offset ; val end = length
    var pos = from ; while (pos < end && !pred(_chars(offset+pos))) pos += 1
    if (pos == end) -1 else pos
  }
  /** Returns the index of the first character that matches `pred`. Returns -1 for no match. */
  def indexOf (pred :Char => Boolean) :Int = indexOf(pred, 0)

  /** Returns the index of the first character that matches `pred` at pos `from` or earlier.
    * Returns -1 if no character matched. */
  def lastIndexOf (pred :Char => Boolean, from :Int) :Int = {
    val offset = _offset
    var pos = from ; while (pos >= 0 && !pred(_chars(offset+pos))) pos -= 1
    pos
  }
  /** Returns the index of the first character that matches `pred`, starting at the last character of
    * the line and seeking backwards. Returns -1 for no match. */
  def lastIndexOf (pred :Char => Boolean) :Int = lastIndexOf(pred, length-1)

  /** Returns the first offset into this line at which `m` matches, starting from `from`.
    * -1 is returned if no match is found. */
  def indexOf (m :Matcher, from :Int = 0) :Int = {
    val offset = _offset ; val n = m.search(_chars, offset+from, offset+length)
    if (n == -1) n else n - offset
  }

  /** Returns the last offset into this line at which `m` matches, starting from `from`.
    * -1 is returned if no match could be found. */
  def lastIndexOf (m :Matcher, from :Int = length-1) :Int = {
    val offset = _offset ; val n = m.searchBackward(_chars, offset, offset+length, offset+from)
    if (n == -1) n else n - offset
  }

  /** Returns the index of the first non-whitespace character on this line, or [[length]] if
    * no-non-whitespace character could be found. */
  def firstNonWS :Int = indexOf(isNotWhitespace, 0) match {
    case -1 => length
    case ii => ii
  }

  /** Returns true if `m` matches this line starting at `start`. NOTE: this is merely a (potentially)
    * more efficient way to say `indexOf(m, start) == start)`, it does not mean that the matcher
    * matches ALL of the remaining characters on the line. */
  def matches (m :Matcher, start :Int = 0) :Boolean =
    m.matches(_chars, _offset+start, _offset+length)

  /** Returns true if the styles of `ss` in `[offset, length)` are equal to the styles in this line
    * in `[start, length)`. */
  def styleMatches (ss :Array[Styles], offset :Int, length :Int, start :Int) :Boolean = {
    if (start + length > this.length) false
    else {
      val tss = _styles ; val toffset = _offset + start
      var ii = 0 ; while (ii < length && (ss(offset+ii) eq tss(toffset+ii))) ii += 1
      ii == length
    }
  }

  /** Returns true if the syntaxes of `xs` in `[offset, length)` are equal to the syntaxes in this
    * line in `[start, length)`. */
  def syntaxMatches (xs :Array[Syntax], offset :Int, length :Int, start :Int) :Boolean = {
    if (start + length > this.length) false
    else {
      val txs = _syns ; val toffset = _offset + start
      var ii = 0 ; while (ii < length && (xs(offset+ii) eq txs(toffset+ii))) ii += 1
      ii == length
    }
  }

  /** Compares this line to `other` lexically and sensitive to case. */
  def compare (other :LineV) :Int = LineV.compare(
    _chars, _offset, length, other._chars, other._offset, other.length, LineV.CaseCmp)

  /** Compares this line to `other` lexically and insensitive to case. */
  def compareIgnoreCase (other :LineV) :Int = LineV.compare(
    _chars, _offset, length, other._chars, other._offset, other.length, LineV.NoCaseCmp)

  /** Returns the contents of this line as a string. */
  def asString :String = new String(_chars, _offset, length)

  override def subSequence (start :Int, end :Int) = new String(_chars, _offset+start, end-start)

  override def equals (other :Any) = other match {
    case ol :LineV => (length == ol.length && compare(ol) == 0 &&
                       ol.styleMatches(_styles, _offset, length, 0) &&
                       ol.syntaxMatches(_syns, _offset, length, 0))
    case _ => false
  }

  override def hashCode = {
    @tailrec def loop (code :Int, chars :Array[Char], ii :Int, last :Int) :Int =
      if (ii == last) code else loop(31*code + chars(ii), chars, ii+1, last)
    loop(1, _chars, _offset, _offset+length)
  }

  /** Returns the `char` array that backs this line. The returned array will only be used to
    * implement read-only methods and will never be mutated. */
  protected def _chars :Array[Char]

  /** Returns the `Styles` array that backs this line. The returned array will only be used to
    * implement read-only methods and will never be mutated. */
  protected def _styles :Array[Styles]

  /** Returns the `Syntax` array that backs this line. The returned array will only be used to
    * implement read-only methods and will never be mutated. */
  protected def _syns :Array[Syntax]

  /** Returns the offset into [[_chars]] and [[_styles]] at which our data starts. */
  protected def _offset :Int
}

/** `LineV` related types and utilities. */
object LineV {

  /** A ordering that sorts lines lexically, sensitive to case. */
  implicit val ordering = new Ordering[LineV]() {
    def compare (a :LineV, b :LineV) :Int = a compare b
  }

  /** A ordering that sorts lines lexically, insensitive to case. */
  val orderingIgnoreCase = new Ordering[LineV]() {
    def compare (a :LineV, b :LineV) :Int = a compareIgnoreCase b
  }

  /** A character comparator function that sorts upper before lower case. */
  val CaseCmp :(Char, Char) => Int = Character.compare

  /** A character comparator function that ignores case. */
  val NoCaseCmp :(Char, Char) => Int =
    (c1, c2) => Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2))

  private def compare (cs1 :Array[Char], o1 :Int, l1 :Int, cs2 :Array[Char], o2 :Int, l2 :Int,
                       cmp :(Char, Char) => Int) :Int = {
    var ii = 0 ; val ll = math.min(l1, l2)
    while (ii < ll) {
      val c1 = cs1(o1+ii) ; val c2 = cs2(o2+ii)
      val ccmp = cmp(c1, c2)
      if (ccmp != 0) return ccmp
      ii += 1
    }
    l1 - l2
  }
}

/** Models a single immutable line of text that is not associated with a buffer.
  *
  * The constructor takes ownership of the supplied arrays. Do not mutate them after using them to
  * create a `Line`. Clone them first if you need to retain the ability to mutate the arrays.
  */
class Line (_cs :Array[Char], _ss :Array[Styles], _xs :Array[Syntax],
            protected val _offset :Int, val length :Int) extends LineV {
  def this (cs :Array[Char], ss :Array[Styles], xs :Array[Syntax]) = this(cs, ss, xs, 0, cs.length)

  require(_cs != null && _ss != null && _xs != null &&
          _cs.length == _ss.length && _cs.length == _xs.length &&
          _offset >= 0 && length >= 0 && length <= (_cs.length - _offset),
          s"Invalid Line args ${_cs} ${_ss} ${_xs} ${_offset} $length")

  override def view (start :Int, until :Int) =
    if (start == 0 && until == length) this else slice(start, until)
  override def slice (start :Int, until :Int) = new Line(_cs, _ss, _xs, _offset+start, until-start)

  override protected def _chars  = _cs
  override protected def _styles = _ss
  override protected def _syns   = _xs

  override def toString () = s"$asString [${_offset}:$length/${_cs.length}]"
}

/** `Line` related types and utilities. */
object Line {

  /** Used to build (immutable) lines with non-default syntax or styles. */
  class Builder (private var _cs :Array[Char]) {
    private var _ss = Array.fill(_cs.length)(Styles.None)
    private var _xs = Array.fill(_cs.length)(Syntax.Default)

    /** Applies `styles` to `[start,end)` of the being-built line. */
    def withStyles (styles :Styles, start :Int = 0, end :Int = _cs.length) :Builder = {
      var ii = start ; while (ii < end) { _ss(ii) = styles ; ii += 1 }
      this
    }

    /** Applies `syntax` to `[start,end)` of the being-built line. */
    def withSyntax (syntax :Syntax, start :Int = 0, end :Int = _cs.length) :Builder = {
      var ii = start ; while (ii < end) { _xs(ii) = syntax ; ii += 1 }
      this
    }

    /** Builds and returns the line. This builder will be rendered unusable after this call. */
    def build () :Line = {
      val l = new Line(_cs, _ss, _xs, 0, _cs.length)
      _cs = null ; _ss = null ; _xs = null
      l
    }
  }

  /** An empty line. */
  final val Empty = apply("")

  /** Creates a line with the contents of `cs` and default styles and syntaxs. */
  def apply (cs :CharSequence) = builder(cs).build()

  /** Creates a line with the contents of `s` and default styles and syntaxs. */
  def apply (s :String) = builder(s).build()

  /** Creates a line builder with `cs` as the line text. */
  def builder (cs :CharSequence) = new Builder(toCharArray(cs))

  /** Creates a line builder with `s` as the line text. */
  def builder (s :String) = new Builder(s.toCharArray)

  /** Creates one or more lines from the supplied text. Newlines are assumed to be equal to
    * [[System.lineSeparator]]. */
  def fromText (text :String) :Seq[Line] =
    // TODO: remove tab hackery when we support tabs
    text.split(System.lineSeparator, -1).map(_.replace('\t', ' ')).map(apply)

  /** Calls [[fromText]] on `text` and tacks on a blank line. */
  def fromTextNL (text :String) = fromText(text) :+ Empty

  /** Converts `lines` to a string which will contain line separators between lines. */
  def toText (lines :Seq[LineV]) :String = {
    val buf = new StringBuilder()
    var ii = 0 ; while (ii < lines.length) {
      if (ii > 0) buf.append(System.lineSeparator)
      buf.append(lines(ii).asString)
      ii += 1
    }
    buf.toString
  }

  /** Converts `cs` into an array of `Char`. */
  def toCharArray (cs :CharSequence) :Array[Char] = {
    val arr = new Array[Char](cs.length)
    var ii = 0 ; while (ii < arr.length) {
      arr(ii) = cs.charAt(ii) ; ii += 1
    }
    arr
  }
}
