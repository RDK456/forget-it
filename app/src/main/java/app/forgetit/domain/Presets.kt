package app.forgetit.domain

/** Bundled quick-pick services. Only name, category and a public account page; no brand imagery. */
data class Preset(val key: String, val name: String, val category: String, val accountUrl: String?, val cycle: Cycle = Cycle.MONTHLY)

val PRESETS = listOf(
    Preset("netflix", "Netflix", "Streaming", "https://www.netflix.com/youraccount"),
    Preset("prime", "Amazon Prime", "Streaming", "https://www.amazon.com/mc", Cycle.YEARLY),
    Preset("disney", "Disney+", "Streaming", "https://www.disneyplus.com/account"),
    Preset("hotstar", "Hotstar", "Streaming", "https://www.hotstar.com/in/account", Cycle.YEARLY),
    Preset("hulu", "Hulu", "Streaming", "https://secure.hulu.com/account"),
    Preset("hbo", "Max", "Streaming", "https://www.max.com/account"),
    Preset("appletv", "Apple TV+", "Streaming", "https://tv.apple.com/settings"),
    Preset("youtube", "YouTube Premium", "Streaming", "https://www.youtube.com/paid_memberships"),
    Preset("spotify", "Spotify", "Music", "https://www.spotify.com/account/subscription/"),
    Preset("applemusic", "Apple Music", "Music", "https://music.apple.com/account/settings"),
    Preset("ytmusic", "YouTube Music", "Music", "https://www.youtube.com/paid_memberships"),
    Preset("audible", "Audible", "Music", "https://www.audible.com/account/overview"),
    Preset("icloud", "iCloud+", "Cloud", "https://www.icloud.com/settings/"),
    Preset("googleone", "Google One", "Cloud", "https://one.google.com/settings"),
    Preset("dropbox", "Dropbox", "Cloud", "https://www.dropbox.com/account/plan"),
    Preset("onedrive", "Microsoft 365", "Software", "https://account.microsoft.com/services", Cycle.YEARLY),
    Preset("adobe", "Adobe Creative Cloud", "Software", "https://account.adobe.com/plans"),
    Preset("chatgpt", "ChatGPT Plus", "Software", "https://chatgpt.com/#settings"),
    Preset("claude", "Claude Pro", "Software", "https://claude.ai/settings/billing"),
    Preset("github", "GitHub", "Software", "https://github.com/settings/billing"),
    Preset("notion", "Notion", "Software", "https://www.notion.so/my-account"),
    Preset("canva", "Canva", "Software", "https://www.canva.com/settings/billing-and-teams"),
    Preset("duolingo", "Duolingo", "Software", "https://www.duolingo.com/settings/super"),
    Preset("xbox", "Xbox Game Pass", "Gaming", "https://account.microsoft.com/services"),
    Preset("playstation", "PlayStation Plus", "Gaming", "https://www.playstation.com/acct/management"),
    Preset("nintendo", "Nintendo Switch Online", "Gaming", "https://accounts.nintendo.com/"),
    Preset("nytimes", "The New York Times", "News", "https://myaccount.nytimes.com/seg/subscription"),
    Preset("medium", "Medium", "News", "https://medium.com/me/settings/membership"),
    Preset("gym", "Gym membership", "Fitness", null),
    Preset("internet", "Internet / broadband", "Utilities", null),
    Preset("mobile", "Mobile plan", "Utilities", null),
)
