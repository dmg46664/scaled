//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled.impl

import com.sun.nio.file.SensitivityWatchEventModifier
import java.io.File
import java.nio.file.{FileSystems, WatchKey, WatchEvent}
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec

/** Used to cancel file and directory watches when they're no longer needed. */
trait Watch {

  /** Cancels the watch that returned this handle. */
  def cancel ()
}

/** A callback interface that is notified when watch events occur. */
abstract class Watcher {

  /** Notification that a file or directory named `child` was created in `dir`. */
  def onCreate (dir :File, child :String) {}

  /** Notification that a file or directory named `child` was deleted in `dir`. */
  def onDelete (dir :File, child :String) {}

  /** Notification that a file or directory named `child` was modified in `dir`. */
  def onModify (dir :File, child :String) {}
}

/** Handles watching the filesystem for changes. */
class WatchManager {
  import java.nio.file.StandardWatchEventKinds._

  private val _service = FileSystems.getDefault.newWatchService()
  private val _watches = new ConcurrentHashMap[WatchKey,WatchImpl]()
  private val _watcher = new Thread("WatchManager") {
    override def run () = { while (true) pollWatches() }
  }
  _watcher.setDaemon(true)
  _watcher.start()

  private case class WatchImpl (key :WatchKey, dir :File, watcher :Watcher) extends Watch {
    def dispatch (ev :WatchEvent[_]) = {
      val kind = ev.kind ; val name = ev.context.toString
      onMainThread(kind match {
        case ENTRY_CREATE => watcher.onCreate(dir, name)
        case ENTRY_DELETE => watcher.onDelete(dir, name)
        case ENTRY_MODIFY => watcher.onModify(dir, name)
        case _ => println(s"Unknown event type [dir=$dir, kind=$kind, ctx=$name]")
      })
    }
    def cancel () {
      key.cancel()
      _watches.remove(key)
    }
  }

  /** Registers a watch on `file`. `watcher` will be invoked (on the main JavaFX thread) when `file`
    * is modified or deleted. */
  def watchFile (file :File, watcher :File => Unit) :Watch = {
    val name = file.getName
    watchDir(file.getParentFile, new Watcher() {
      override def onModify (dir :File, child :String) = if (child == name) watcher(file)
      override def onDelete (dir :File, child :String) = if (child == name) watcher(file)
    })
  }

  /** Registers a watch on `dir`. `watcher` will be invoked (on the main JavaFX thread) when any
    * files are created, modified or deleted in `dir`. */
  def watchDir (dir :File, watcher :Watcher) :Watch = {
    val kinds = Array(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY).
      asInstanceOf[Array[WatchEvent.Kind[_]]] // oh Scala, you devil
    val key = dir.toPath.register(_service, kinds, SensitivityWatchEventModifier.HIGH)
    val impl = WatchImpl(key, dir, watcher)
    _watches.put(key, impl)
    impl
  }

  private def pollWatches () :Unit = try {
    import scala.collection.convert.WrapAsScala._
    // wait for key to be signalled
    val key = _service.take()
    val watcher = _watches.get(key)
    if (watcher != null) {
      key.pollEvents() foreach watcher.dispatch
      // if we can't reset the key (the dir went away or something), clear it out
      if (!key.reset()) watcher.cancel()
    }
  } catch {
    case ie :InterruptedException => // loop!
    case ex :Exception => ex.printStackTrace(System.err)
  }
}