import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.extractors.NewVoeExtractor
import com.lagradost.extractors.PeytonepreExtractor

@CloudstreamPlugin
class ToonItaliaXYZPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner
        registerMainAPI(ToonItaliaXYZ())
        registerExtractorAPI(NewVoeExtractor())
        registerExtractorAPI(PeytonepreExtractor())
    }
}