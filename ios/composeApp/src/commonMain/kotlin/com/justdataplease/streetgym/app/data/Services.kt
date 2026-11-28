package com.justdataplease.streetgym.app.data

/**
 * What the phone gives the app beyond its screens: the home-screen [widget] (WidgetKit on iPhone), the [account]
 * (Firebase Auth with Google and Sign in with Apple) and the cloud's [documents] (Firestore), [region] (the phone's
 * country, ISO two letters: the leaderboard's own country and the profile's first pick), [appSet] (the install's chain
 * id for progress analytics: the identifier for vendor on iPhone), [versionCode] (the build number) and [analytics]
 * (Google Analytics through Firebase Analytics; null without Firebase in the build). Everything has a stand-in that does
 * nothing, so previews, tests and a build without the configuration run as before.
 */
class PhoneServices(
    val widget: HomeWidget = HomeWidget.None,
    val account: Account = Account.None,
    val documents: CloudDocuments? = null,
    val region: String? = null,
    val appSet: AppSetSource = AppSetSource.None,
    val versionCode: Int = 1,
    val analytics: AnalyticsSink? = null,
)
