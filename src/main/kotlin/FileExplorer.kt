import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.ProgressBar
import javafx.scene.control.TextField
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class FileExplorer(var status: TextField, var progress: ProgressBar) : Command() {

    var path = "/"

    private fun makeFile(out: String): AndroidFile? {
        val bits = ArrayList<String>()
        for (bit in out.split(' '))
            if (bit.isNotBlank()) {
                if (bit == "->")
                    break
                bits.add(bit)
            }
        return when {
            bits.size < 6 -> null
            bits[5].length == 10 && bits[6].length == 5 -> AndroidFile(
                bits[0][0] != '-',
                bits.drop(7).joinToString(" ").trim(),
                bits[4].toInt(),
                "${bits[5]} ${bits[6]}"
            )
            bits[4].length == 10 && bits[5].length == 5 -> AndroidFile(
                bits[0][0] != '-',
                bits.drop(6).joinToString(" ").trim(),
                bits[3].toInt(),
                "${bits[4]} ${bits[5]}"
            )
            bits[3].length == 10 && bits[4].length == 5 -> AndroidFile(
                bits[0][0] != '-',
                bits.drop(5).joinToString(" ").trim(),
                0,
                "${bits[3]} ${bits[4]}"
            )
            else -> null
        }

    }

    fun navigate(where: String) {
        if (where == "..") {
            if (path.split('/').size < 3)
                return
            path = path.dropLast(1).substringBeforeLast('/') + "/"
        } else path += "$where/"
    }

    fun getFiles(): ObservableList<AndroidFile> {
        val files = FXCollections.observableArrayList<AndroidFile>()
        exec("adb shell ls -l $path", lim = 5).trim().lines().forEach {
            if ("ls:" !in it && ':' in it)
                makeFile(it)?.let { file ->
                    files.add(file)
                }
        }
        return files
    }

    private fun format(pathname: String): String = "'$pathname'"

    private fun init(command: String = "adb") {
        pb.redirectErrorStream(false)
        status.text = ""
        proc = pb.start()
        val scan = Scanner(proc.inputStream, "UTF-8").useDelimiter("")
        while (scan.hasNextLine()) {
            val output = scan.nextLine()
            Platform.runLater {
                if ('%' in output)
                    progress.progress = output.substringBefore('%').trim('[', ' ').toInt() / 100.0
                else if (command in output)
                    status.text = "ERROR: ${output.substringAfterLast(':').trim()}"
            }
        }
        scan.close()
        proc.waitFor()
    }

    fun pull(selected: List<AndroidFile>, to: File, func: () -> Unit) {
        thread(true) {
            if (selected.isEmpty()) {
                pb.command("${prefix}adb", "pull", path, to.absolutePath)
                init()
            } else {
                selected.forEach {
                    pb.command("${prefix}adb", "pull", path + it.name, to.absolutePath)
                    init()
                }
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
    }

    fun push(selected: List<File>, func: () -> Unit) {
        thread(true) {
            selected.forEach {
                pb.command("${prefix}adb", "push", it.absolutePath, path)
                init()
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
    }

    fun delete(selected: List<AndroidFile>, func: () -> Unit) {
        thread(true) {
            selected.forEach {
                if (it.dir)
                    pb.command("${prefix}adb", "shell", "rm", "-rf", format(path + it.name))
                else pb.command("${prefix}adb", "shell", "rm", "-f", format(path + it.name))
                init("rm")
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
    }

    fun mkdir(name: String, func: () -> Unit) {
        thread(true) {
            pb.command("${prefix}adb", "shell", "mkdir", format(path + name))
            init("mkdir")
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
    }

    fun rename(selected: AndroidFile, to: String, func: () -> Unit) {
        thread(true) {
            pb.command("${prefix}adb", "shell", "mv", format(path + selected.name), format(path + to))
            init("mv")
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
    }

}