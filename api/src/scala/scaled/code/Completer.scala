//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled.code

import com.google.common.collect.TreeMultimap
import java.util.{HashMap, LinkedHashSet}
import scaled._
import scaled.util.Chars

/** Provides a pluggable mechanism for code completion. */
abstract class Completer {

  /** Generates a list of completions for the code at `pos`. */
  def completeAt (buffer :Buffer, pos :Loc) :Future[Completer.Result]
}

object Completer {

  /** Encapsulates the result of a completion request.
    * @param start the start of the 'prefix' that was used by the completer.
    * @param length the length of the 'prefix' used by the completer.
    * @param comps the completions for the prefix previously specified.
    * @param index the index of the completion to use (generally starts at zero & is used by
    * CodeMode to track position when cycling through completions).
    */
  case class Result (start :Loc, length :Int, comps :SeqV[String], index :Int) {
    /** The currently active completion. */
    def active :String = comps(index)
    /** Returns a result configured with the next completion active. */
    def next :Result = copy(index = (index+1) % comps.length)
  }

  /** Returns a completer for use with `buffer`. */
  def completerFor (wspace :Workspace, buffer :Buffer) :Completer = {
    // TODO: something more pluggable
    wspace.state.get[TokenCompleter] match {
      case Some(comp) => comp
      case None =>
        val comp = new TokenCompleter(wspace)
        wspace.state.set[TokenCompleter](comp)
        comp
    }
  }
}

class TokenCompleter (val wspace :Workspace) extends Completer {

  final val MinRefreshInterval = 3000L

  class Tokener (val buffer :RBuffer) {
    val tokens = TreeMultimap.create[String,String]()
    var needsRefresh = true
    var lastRefresh = 0L

    buffer.edited.onEmit { needsRefresh = true }

    def addCompletions (prefix :String, sb :LinkedHashSet[String]) {
      if (needsRefresh) {
        val now = System.currentTimeMillis
        if (now - lastRefresh > MinRefreshInterval) {
          refresh()
          lastRefresh = now
          needsRefresh = false
        }
      }

      val iter = tokens.keySet.tailSet(prefix, true).iterator
      def loop () {
        val word = iter.next()
        if (word.startsWith(prefix)) {
          tokens.get(word) foreach sb.add
          if (iter.hasNext()) loop()
        }
      }
      if (iter.hasNext()) loop()
    }

    private def refresh () {
      tokens.clear()
      buffer.lines foreach { line =>
        val IsWord = Chars.isWord
        val sb = new java.lang.StringBuilder() // don't use Scala's retarded SB
        def flush () {
          if (sb.length > 0) {
            // ignore short tokens, not much point in completing a three letter word
            if (sb.length > 3) {
              val word = sb.toString
              val key = word.toLowerCase
              tokens.put(key, word)
            }
            sb.setLength(0)
          }
        }
        var ii = 0 ; val ll = line.length ; while (ii < ll) {
          val c = line.charAt(ii)
          val isToken = IsWord(c) || c == '_' // TODO: pluggable tokenizers?
          if (isToken) sb.append(c)
          else flush()
          ii += 1
        }
        flush()
      }
    }
  }

  private val tokeners = new HashMap[Buffer,Tokener]()
  private def tokener (buffer :Buffer) = tokeners.get(buffer) match {
    case null =>
      // note: fix naughtiness (have Workspace give out RBuffer?)
      val rbuffer = buffer.asInstanceOf[RBuffer]
      val ntok = new Tokener(rbuffer)
      tokeners.put(buffer, ntok)
      rbuffer.killed.onEmit { tokeners.remove(buffer) }
      ntok
    case tok => tok
  }

  override def completeAt (buffer :Buffer, pos :Loc) = {
    val (pstart, pend) = Chars.wordBoundsAt(buffer, pos)
    val token = buffer.region(pstart, pend).map(_.asString).mkString
    val prefix = token.toLowerCase
    // don't complete single char prefixes & don't complete if we're at the start of the token (you
    // should be at the end, but we'll also allow being in the middle; otherwise this ends up
    // re-completing the first token on a line if you press tab repeatedly from column zero)
    val comps = if (prefix.length < 2 || pos == pstart) Seq() else {
      val matches = new LinkedHashSet[String]()
      // first add completions from the same buffer
      tokener(buffer).addCompletions(prefix, matches)
      // then add completions from all other (non-ephmeral) open buffers
      for (obuffer <- wspace.buffers) {
        if (obuffer != buffer && !Buffer.isScratch(obuffer.name)) {
          tokener(obuffer).addCompletions(prefix, matches)
        }
      }
      // if the token is mixed case, filter out all matches that don't match case exactly
      if (Chars.mixedCase(token)) matches.removeIf(word => !word.startsWith(token))
      // remove the exact token on which we matched
      matches.remove(token)
      // if we have >0 other matches, add our prefix back as the final match
      if (matches.size() > 0) matches.add(token)
      Seq.builder[String]().append(matches).build()
    }
    Future.success(Completer.Result(pstart, prefix.length, comps, 0))
  }
}
