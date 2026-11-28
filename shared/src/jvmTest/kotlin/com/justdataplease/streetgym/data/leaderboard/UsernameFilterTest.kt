package com.justdataplease.streetgym.data.leaderboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The username filter both phones use (Apple 1.2): what it refuses, how it reads a name, and what it leaves alone. */
class UsernameFilterTest {
    private fun refused(vararg names: String) = names.forEach { assertFalse(UsernameFilter.allowed(it), "$it should be refused") }
    private fun allowed(vararg names: String) = names.forEach { assertTrue(UsernameFilter.allowed(it), "$it should be allowed") }

    @Test
    fun obscenitiesAreRefusedAnywhereInAName() {
        refused("fuck", "FuckYou", "motherfucker99", "bigfuckingdeal", "bullshit_king", "the_asshole", "cuntface",
            "bitch.please", "dickhead", "pussy_slayer", "porno_star", "jizzmaster")
    }

    @Test
    fun separatorsAndCapitalsDontHideAWord() {
        refused("f_u_c_k", "f.u.c.k", "a_s_s", "s.h.i.t", "Big_Ass", "BigAss", "BigDick69", "ass69", "69ass", "AdMin")
    }

    @Test
    fun lookAlikesAndRepeatedLettersReadAsTheWord() {
        refused("fuuuuck", "FUUUCK", "fvck_you", "sh1t", "5hit", "a55", "a55hole", "d1ck", "b1tch",
            "c0ck", "p0rn", "n4zi", "h1tler", "@ss", "a\$\$", "pen15", "kkkk", "shiiit", "asss", "f4g", "phuk")
    }

    @Test
    fun shortWordsCountOnlyAsTheWholeNameOrAWholePart() {
        refused("ass", "dick", "cock", "shit", "tits", "anal", "rape", "rapist", "porn", "sex", "nazi", "the_rapist",
            "TheRapist", "dick_2000", "Shit.Show")
        // The Scunthorpe problem: ordinary words and names that contain them.
        allowed("assassin", "class", "Classic_Mike", "therapist", "cockerel", "dickson", "Dickens", "grape",
            "grapefruit", "Scunthorpe", "Penistone", "Clitheroe", "analyst", "canal", "Hancock", "Hitchcock", "Babcock",
            "Sussex", "Essex", "Sexton", "Hassan", "Massimo", "Cassandra", "bassist", "passion", "Yamashita",
            "Kinoshita", "Matsushita", "Dikshit", "Shital", "Fukuoka", "Phuket", "Nazim", "Nazir", "Ashkenazi",
            "Arsenal", "Marsch", "computer", "reputation", "Dominique", "Monique", "technique", "Africa", "Vergara",
            "calculo", "Malakai", "raccoon", "Japan", "Pakistan", "spice", "Badminton", "mood", "good_mood", "pineapple", "snigger", "Niger", "Nigel", "bigger", "Thorny", "Georgy", "humanflag", "Pornchai", "Merdan",
            "Wankel", "Sniggers", "therapists", "draped", "scraping", "as_fast_as", "run_as_one", "Annalisa", "Shitake")
    }

    @Test
    fun slursAndHateAreRefused() {
        refused("nigger", "n1gga", "faggot", "tranny", "wetback", "spic", "chink", "paki", "retard", "Retarded_Joe",
            "hitler", "AdolfHitler", "heil_hitler", "siegheil", "neonazi", "nazi_88", "kkk", "KKK_member", "kukluxklan",
            "john1488", "14words", "swastika", "whitepower", "white_pride")
        // White Power is refused; a powerlifter named White isn't. Aryan and Kike (Enrique in Spanish) are given names.
        allowed("white_powerlifter", "Aryan_Khan", "Kike_Garcia")
    }

    @Test
    fun greekInLatinLettersAndOtherLanguagesAreRefused() {
        refused("malakas", "MALAKAS_7", "malakes", "malaka", "malakismenos", "gamo", "gamw", "gamoto", "gamwto",
            "gamhsou", "gamisou", "gamiesai", "gamimenos", "poutana", "poutanes", "poutsa", "poustis", "kariolis",
            "kariola", "mouni", "mounara", "arxidia", "skata", "keratas", "tsoulana")
        refused("scheisse", "arschloch", "Hurensohn", "wichser", "fotze", "fick_dich", "schlampe")
        refused("merde", "putain", "salope", "connard", "encule", "nique")
        refused("mierda", "puta", "hijodeputa", "cabron", "pendejo", "gilipollas", "maricon", "joder")
        refused("cazzo", "vaffanculo", "stronzo", "puttana", "minchia", "coglione", "troia")
        refused("caralho", "kurwa", "blyat", "cyka_blyat", "pizda")
        // Greek surnames and the Pacific name Malakai stay.
        allowed("Malakasis", "Malakopoulos", "Gamopoulos", "Kolarakis", "Poutachidis", "Kostas", "Nikos", "Kalisthenis")
    }

    @Test
    fun namesThatPassForTheAppOrItsStaffAreReserved() {
        refused("admin", "Admin", "ADMIN", "administrator", "the_admin", "admin_1", "moderator", "mod", "Mod_Team",
            "support", "Support.Team", "staff", "official", "Official_Coach", "streetgym", "StreetGym", "street_gym",
            "Street.Gym", "streetgym_official", "TheStreetGym", "justdataplease", "JustDataPlease", "apple", "Apple_Inc",
            "google", "GoogleFan", "sysadmin", "4dm1n")
        allowed("modern", "model_athlete", "supporter", "staffy", "officially_strong", "applejack", "pineapple",
            "street_workout", "gym_rat", "badminton")
    }

    @Test
    fun caseDoesntMatter() {
        refused("FUCK", "Fuck", "fUcK", "SHIT", "Shit", "ASSHOLE", "MaLaKaS", "NAZI")
        allowed("CLASS", "Class", "ASSASSIN", "Assassin", "SCUNTHORPE", "DICKSON", "DICKENS")
    }

    @Test
    fun ordinaryAthletesNamesAreAllowed() {
        allowed("ana", "ben", "Alexa_7", "Alexa8", "maria.k", "Bar_Wolf", "lena_runs", "tomas", "H4ndstand", "ella.moves",
            "pullup_pat", "Runner", "Scorer", "charlie", "Bravo", "delta", "Street_Ana", "muscle_up", "planche.pro",
            "front_lever", "calisthenics_life", "Spartan300", "Titan", "Hercules", "beast_mode", "Giorgos_GR",
            "Dimitris", "Eleni", "Maximilian", "Francois", "Jose_Luis", "Giuseppe", "Joao", "Sven", "Oskar", "Aisha",
            "Mohammed", "Kenji", "Yuki", "Priya", "Chen", "Olumide", "Ana_Lopez", "AnaLopez", "John_Smith_88", "xX_Ninja_Xx",
            "Assault_Course", "Passion_Fit", "ClassAct", "MassGainer", "abc", "a.b_c", "ABCDEFGHIJKLMNOPQRST")
    }

    @Test
    fun anEmptyNameIsLeftToTheFormatCheck() {
        allowed("", "___", "...")
    }
}
