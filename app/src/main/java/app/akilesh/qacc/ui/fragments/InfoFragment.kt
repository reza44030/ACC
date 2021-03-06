package app.akilesh.qacc.ui.fragments

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import app.akilesh.qacc.Const.Links.githubReleases
import app.akilesh.qacc.Const.Links.githubRepo
import app.akilesh.qacc.Const.Links.telegramChannel
import app.akilesh.qacc.Const.Links.telegramGroup
import app.akilesh.qacc.Const.Links.xdaThread
import app.akilesh.qacc.R
import app.akilesh.qacc.utils.AppUtils.getColorAccent
import com.danielstone.materialaboutlibrary.ConvenienceBuilder
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList

class InfoFragment: MaterialAboutFragment() {

    override fun getTheme(): Int {
        var theme: Int = R.style.AppTheme
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> {
                theme =  R.style.AppTheme_AboutCardLight
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                theme = R.style.AppTheme_AboutCardDark
            }
        }
        return theme
    }

    override fun getMaterialAboutList(context: Context?): MaterialAboutList {

        var tintColor = ResourcesCompat.getColor(requireContext().resources, R.color.colorPrimary, requireContext().theme)
        val icons = listOf(
            ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_outline_info, requireContext().theme),
            ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_github, requireContext().theme),
            ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_outline_group, requireContext().theme),
            ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_xda, requireContext().theme),
            ResourcesCompat.getDrawable(requireContext().resources, R.drawable.ic_outline_get_app, requireContext().theme)
        )
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val useSystemAccent = sharedPreferences.getBoolean("system_accent", false)
        if (useSystemAccent) {
            tintColor = requireContext().getColorAccent()
            icons.forEach {
                it?.setTint(tintColor)
            }
        }

        val appInfoCard = MaterialAboutCard.Builder()
            .addItem(
                MaterialAboutTitleItem.Builder()
                    .text(requireContext().resources.getString(R.string.app_name))
                    .desc(getString(R.string.about_title_desc))
                    .icon(R.mipmap.ic_launcher)
                    .build()
            )
            .addItem(
                ConvenienceBuilder.createVersionActionItem(context,
                    icons[0],
                    getString(R.string.version),
                    false
                )
            )
            .build()


        val linksCard = MaterialAboutCard.Builder()
            .title(getString(R.string.links))
            .titleColor(tintColor)
            .addItem(
                MaterialAboutActionItem.Builder()
                    .text(getString(R.string.github_repo))
                    .icon(icons[1])
                    .setOnClickAction(
                        ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse(githubRepo))
                    )
                    .build()
            )
            .addItem(
                MaterialAboutActionItem.Builder()
                    .text(getString(R.string.telegram_group))
                    .icon(icons[2])
                    .setOnClickAction(
                        ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse(telegramGroup))
                    )
                    .build()
            )
            .addItem(
                MaterialAboutActionItem.Builder()
                    .text(getString(R.string.xda_thread))
                    .icon(icons[3])
                    .setOnClickAction(
                        ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse(xdaThread))
                    )
                    .build()
            )
            .build()

        val downloadsCard = MaterialAboutCard.Builder()
            .title(getString(R.string.downloads))
            .titleColor(tintColor)
            .addItem(
                MaterialAboutActionItem.Builder()
                    .text(getString(R.string.github_releases))
                    .icon(icons[4])
                    .setOnClickAction(
                        ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse(githubReleases))
                    )
                    .build()
            )
            .addItem(
                MaterialAboutActionItem.Builder()
                    .text(getString(R.string.telegram_channel))
                    .icon(icons[4])
                    .setOnClickAction(
                        ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse(telegramChannel))
                    )
                    .build()
            )
            .build()


        return MaterialAboutList.Builder()
            .addCard(appInfoCard)
            .addCard(linksCard)
            .addCard(downloadsCard)
            .build()
    }
}