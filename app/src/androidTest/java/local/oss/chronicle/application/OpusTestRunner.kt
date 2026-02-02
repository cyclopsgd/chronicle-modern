package local.oss.chronicle.application

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class OpusTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, TestOpusApplication::class.java.name, context)
    }
}
