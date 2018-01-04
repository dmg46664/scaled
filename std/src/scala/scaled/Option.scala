//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled

import java.util.{NoSuchElementException, Optional}

/** Represents an optional value. */
sealed abstract class Option[+A] {

  /** Returns the option's value.
    * @throws NoSuchElementException if called on `None`. */
  def get :A

  /** Returns `true` if this option contains a value, `false` otherwise. */
  def isDefined :Boolean

  /** Returns `false` if this option contains a value, `true` otherwise. */
  def isEmpty :Boolean = !isDefined

  /** Applies `f` to this option's value, if defined, wrapping the result in an option. */
  @inline final def map[B] (f :A => B) :Option[B] = if (isDefined) Some(f(get)) else None

  /** Returns `None` if this option is empty, otherwise `f(get)`.
    * This is monadic bind for the `Option` monad. */
  @inline final def flatMap[B] (f :A => Option[B]) :Option[B] = if (isDefined) f(get) else None

  /** Returns `isEmpty` if this option is empty, otherwise `f(get)`. */
  @inline final def fold[B] (ifEmpty : =>B)(f :A => B) :B = if (isDefined) f(get) else ifEmpty

  /** Applies `op` to this option, if it is defined. */
  @inline final def foreach[U] (op :A => U) :Unit = if (isDefined) op(get) else None

  /** Returns `get` if this option is defined, `other` if not. Note that `other` is lazy. */
  @inline final def getOrElse[B >: A] (other : =>B) :B = if (isDefined) get else other

  /** Applies `op` to this option, if it is defined. */
  @inline final def ifDefined[U] (op :A => U) :Unit = if (isDefined) op(get)

  /** Returns `this` if non-empty, `opt` otherwise. Note that `opt` is lazy. */
  @inline final def orElse[B >: A] (opt : =>Option[B]) :Option[B] = if (isDefined) this else opt

  /** An alias for [[getOrElse]]. Useful with maps: `foo.get(bar) || baz`. */
  @inline final def ||[B >: A] (other : =>B) :B = if (isDefined) get else other

  /** Returns `get` if this option is defined, `other` if not. Note that `other` is **strict**. */
  @inline final def or[B >: A] (other :B) :B = if (isDefined) get else other

  /** Returns `Right(get)` if this option is defined, `Left(left)` if not. */
  @inline final def orLeft[B] (left : =>B) :Either[B,A] =
    if (isDefined) Right(get) else Left(left)

  /** Returns `Left(get)` if this option is defined, `Right(right)` if not. */
  @inline final def orRight[B] (right : =>B) :Either[A,B] =
    if (isDefined) Left(get) else Right(right)

  /** Views this option as an `Iterable`. */
  def toIterable :Iterable[A] = toSeq

  /** Converts this option to a `List`. */
  def toList :List[A] = if (isDefined) get :: Nil else Nil

  /** Converts this option to a `Seq`. */
  def toSeq :Seq[A] = if (isDefined) Std.seq(get) else Seq.empty

  /** Converts this Scaled `Option` to a Scala `Option`. */
  def toScala :SOption[A] = if (isDefined) SSome(get) else SNone
}

/** Static [[Option]] things. */
object Option {

  /** Returns an option which contains `value`. */
  def some[A] (value :A) :Option[A] = new Some(value)

  /** Returns an option which contains no value (is empty). */
  def none[A] :Option[A] = None.asInstanceOf[Option[A]]

  /** Returns an option which contains `value` iff value is not null, [[none]] otherwise. */
  def apply[A] (value :A) :Option[A] = if (value == null) none else some(value)

  /** Creates a Scaled `Option` from a Scala `Option`. */
  def from[A] (opt :SOption[A]) :Option[A] = if (opt.isDefined) Option(opt.get) else None

  /** Creates a Scaled `Option` from a Java `Optional`. */
  def from[A] (opt :Optional[A]) :Option[A] = if (opt.isPresent) Option(opt.get) else None

  private def build[A] (elems :Array[Any], size :Int) :Option[A] = size match {
    case 0 => none
    case 1 => some(elems(0).asInstanceOf[A])
    case n => throw new UnsupportedOperationException(
      s"Too many elements accumulated to Option.Builder: $n")
  }

  private final class Some[+A] (value :A) extends Option[A] {
    override def isDefined = true
    override def get = value
    override def hashCode = if (value == null) 0 else value.hashCode
    override def equals (other :Any) = other match {
      case oopt :Some[_] => value == oopt.get
      case _ => false
    }
    override def toString = s"Some($value)"
  }
}

/** Introduction and elimination of `Some`. */
object Some {

  /** Returns an [[Option]] that contains `value`. */
  def apply[A] (value :A) :Option[A] = Option.some(value)

  /** Unapplies an `Option` for use in pattern matching. */
  def unapply[A] (opt :Option[A]) :Option[A] = opt
}

/** The [[Option]] that contains no value. */
case object None extends Option[Nothing] {

  override def get = throw new NoSuchElementException("Called get on empty Option.")
  override def isDefined = false
  override def hashCode = 0
  override def equals (other :Any) = this eq other.asInstanceOf[AnyRef]
  override def toString = "None"
}
