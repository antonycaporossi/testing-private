import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.extractors.MaxStreamExtractor
import com.lagradost.extractors.StreamTapeExtractor

@CloudstreamPlugin
class ToonItaliaPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner
        registerMainAPI(ToonItalia())
        registerExtractorAPI(StreamTapeExtractor())
        registerExtractorAPI(MaxStreamExtractor())
    }
}