package com.justdataplease.streetgym.data.leaderboard

/**
 * The words [UsernameFilter] refuses in a username: written for Street Gym (no published list was copied), in lower-case
 * ASCII as a username spells them, each letter written as often as the word needs it (the filter reads a longer run as
 * the same word, so "fuck" covers "fuuuck" and "1488" covers "14888"). English, Greek in Latin letters, German, French,
 * Spanish and Italian, with the commonest Portuguese and Slavic ones, hate terms, and names that pass for the app or its
 * staff. One list for both phones: a word added here is refused on Android and iPhone alike.
 *
 * A word goes in [ANYWHERE] only when no ordinary name or word contains it ("shit" is in Yamashita and Kinoshita,
 * "nazi" in Nazim and Ashkenazi, "ass" in Hassan, "puta" in reputation), else in [WHOLE], with its compounds that
 * are safe anywhere ("bullshit", "neonazi", "asshole", "hijodeputa") in [ANYWHERE]. An ordinary word that still
 * contains one goes in [ORDINARY].
 */
internal object UsernameWords {
    /** Refused anywhere in a name, whatever is around them. */
    val ANYWHERE: List<String> = listOf(
        // English
        "anilingus", "apeshit", "arsehole", "assclown", "asshat", "asshole", "asskiss", "asslick", "asswipe",
        "bastard", "batshit", "bdsm", "bellend", "bestiality", "biatch", "bigcock", "bigdick", "biotch", "bitch",
        "blowjob", "bollock", "boobs", "bukkake", "bullshit", "butthole", "buttplug", "chickenshit", "clit",
        "cocksuck", "cumshot", "cunnilingus", "cunt", "deepthroat", "dickhead", "dildo", "dipshit", "douchebag",
        "dumbass", "eatshit", "fatass", "fcuk", "felch", "fellatio", "fisting", "fuck", "fvck", "gangbang", "goatse",
        "handjob", "horseshit", "incest", "jackass", "jackoff", "jerkoff", "jizz", "killyourself", "kissmyass",
        "knobhead", "masturbat", "motherfuck", "necrophil", "orgasm", "paedophil", "pedobear", "pedofil", "pedophil",
        "penis", "phuck", "porno", "pornhub", "pornstar", "pussy", "queef", "rimjob", "schlong", "shitbag",
        "shitface", "shithead", "shithole", "shitty", "skank", "slut", "smartass", "smegma", "titties", "titty",
        "twat", "vagina", "wanker", "wanking", "whore", "zoophil", "2g1c",
        // English slurs
        "beaner", "faggot", "golliwog", "jewboy", "junglebunny", "mongoloid", "nigga", "nigger", "porchmonkey",
        "raghead", "retarded", "shemale", "towelhead", "tranny", "wetback", "zipperhead",
        // Hate
        "aryannation", "gasthejews", "goebbels", "hakenkreuz", "himmler", "hitler", "judensau", "killjews",
        "killthejews", "kkk", "kukluxklan", "mengele", "neonazi", "siegheil", "supremacist", "swastika",
        "untermensch", "whitepower", "whitepride", "zyklonb", "14words", "1488",
        // Greek in Latin letters
        "arxidi", "gamato", "gamhmen", "gamhse", "gamhsou", "gamies", "gamimen", "gamiol", "gamisou", "gamoto",
        "gamwto", "kariol", "karyol", "kavlos", "keratas", "kolaras", "kolotrip", "kolotryp", "kwlara", "kwlotryp",
        "malakas", "malakes", "malakies", "malakism", "mounara", "mounopano", "mounosk", "mpastard", "pousth",
        "pousti", "poustak", "poustar", "poutana", "poutanes", "poutanog", "poutsa", "poutses", "psolh", "psoli",
        "putana", "skatofatsa", "tsoulana",
        // German
        "arschfick", "arschgeige", "arschloch", "ficken", "ficker", "fickdich", "fickfresse", "fotze", "hurensohn",
        "kanake", "leckmich", "missgeburt", "schlampe", "scheiss", "schwuchtel", "titten", "verpiss", "wichser",
        // French
        "bougnoule", "branleur", "chinetoque", "connard", "connasse", "couille", "encule", "filsdepute", "merde",
        "niquetamere", "putain", "salope", "youpin",
        // Spanish
        "cabron", "chingada", "chingado", "chingatu", "culero", "follar", "gilipollas", "hijodeputa", "hijoputa",
        "joder", "malparido", "maricon", "mierda", "pendejo", "putamadre", "sudaca",
        // Italian
        "cazzo", "coglion", "diocane", "dioporco", "fanculo", "frocio", "minchia", "mignotta", "pompino",
        "porcamadonna", "porcodio", "puttana", "stronza", "stronzo", "zoccola",
        // Portuguese
        "arrombado", "buceta", "caralho", "filhodaputa", "fodase",
        // Polish, Russian and Serbo-Croatian in Latin letters
        "blyat", "cyka", "jebac", "kurwa", "nahui", "nahuy", "pidaras", "pidor", "pizda",
        // Names that pass for the app or its staff ("street_gym" reads "streetgym")
        "administrator", "google", "justdataplease", "moderator", "streetgym", "sysadmin",
    )

    /** Refused only as the whole name or a whole part ("big_ass", "BigAss", "ass69"), never inside another word. */
    val WHOLE: List<String> = listOf(
        // English
        "anal", "anus", "arse", "ass", "asses", "boobies", "cock", "cocks", "cuck", "cum", "dick", "dicks", "fck",
        "fuk", "fuq", "horny", "milf", "orgy", "paedo", "pedo", "phuk", "porn", "rape", "raped", "rapes", "raping",
        "rapist", "rapists", "sex", "shit", "shite", "shits", "tit", "tits", "wank",
        // English slurs
        "chink", "chinks", "coon", "coons", "dyke", "dykes", "fag", "fagot", "fags", "gook", "gooks", "gyppo",
        "homo", "jap", "japs", "lesbo", "paki", "pakis", "pikey", "redskin", "retard", "retards", "spic", "spics",
        "squaw",
        // Hate
        "heil", "nazi", "nazis",
        // Greek in Latin letters
        "archidi", "archidia", "gamo", "gamw", "kerata", "kolara", "malaka", "malakia", "mouni", "skata", "vizia",
        "vyzia",
        // German
        "arsch", "fick", "hure", "huren", "neger", "nutte", "pimmel", "schwanz", "spast", "spasti",
        // French
        "batard", "bicot", "negre", "nique", "pede", "pute", "putes", "tapette",
        // Spanish
        "chinga", "culo", "marica", "polla", "pollas", "puta", "putas", "puto", "putos", "verga", "zorra",
        // Italian
        "fica", "figa", "merda", "troia",
        // Portuguese
        "porra", "viado",
        // Names that pass for the app or its staff
        "admin", "admins", "apple", "mod", "mods", "official", "staff", "support",
    )

    /**
     * Ordinary words and names that contain a word above, or read as one with a doubled letter ("mood" as "mod",
     * "annus" as "anus"): left out before [ANYWHERE] is searched, and never refused as a whole part.
     */
    val ORDINARY: List<String> = listOf(
        "annal", "annus", "clitheroe", "japp", "malakasis", "merdeka", "mood", "moods", "niggard", "peniston",
        "powerlift", "pussycat", "putte", "putto", "scunthorpe", "snigger",
    )
}
