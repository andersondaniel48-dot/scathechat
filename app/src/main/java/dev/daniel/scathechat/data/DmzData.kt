package dev.daniel.scathechat.data

/**
 * MW4 DMZ reference data: Hajin and the X01 black site.
 * Positions are tac-map grid squares (what key tooltips give you in game), compiled from community guides
 * (codcentral.net, hajindmzkeys.com, dmzmap.com, gamerant.com) in November 2026. Anything not yet confirmed says so.
 */
object Dmz {
    enum class Kind(val label: String) {
        KEY("Key room"), POI("Location"), AREA("Area"), EXFIL("Exfil"), BOSS("Boss"),
        STATION("Station"), POLICE("Police station"), BANK("Bank branch"), FIRE("Fire station"),
        MARKET("Black market"), ENTRY("Entrance"),
    }

    enum class Threat(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High"), CRITICAL("Critical"), EXTREME("Extreme"), NONE("") }

    /** [sources]: map images (with the tac-map grid drawn on) that each phone downloads for itself. */
    data class MapDef(val id: String, val title: String, val short: String, val cols: Int, val rows: Int, val rowStart: Int, val sources: List<String>)

    val maps = listOf(
        MapDef("hajin", "Hajin", "Hajin", 10, 9, 1, listOf(
            "https://codcentral.net/assets/library/mw4/dmz/map/hajin-map-1440.webp",
            "https://codmunity.gg/dmz/hajin-map-cod2-1120.webp",
        )),
        MapDef("x01_lower", "X01 · Lower floor (Deep Vault)", "X01 Lower", 10, 9, 0, listOf(
            "https://codcentral.net/assets/library/mw4/dmz/map/x01-floor-1-v4.webp",
        )),
        MapDef("x01_upper", "X01 · Upper floor (Cargo Bay)", "X01 Upper", 10, 9, 0, listOf(
            "https://codcentral.net/assets/library/mw4/dmz/map/x01-floor-2-v4.webp",
        )),
    )
    fun map(id: String) = maps.first { it.id == id }

    data class Spot(
        val id: String, val map: String, val name: String, val kind: Kind, val grid: String,
        val place: String, val detail: String, val rarity: String? = null,
    )

    /** A named location drawn as a zone. cx/cy are in grid cells from the top-left (0..cols, 0..rows). */
    data class Zone(val map: String, val name: String, val cx: Float, val cy: Float, val r: Float, val threat: Threat, val note: String = "")

    /** Grid "F4" -> column/row index on this map, or null. */
    fun cell(map: MapDef, grid: String): Pair<Int, Int>? {
        val g = grid.trim().uppercase()
        if (g.length < 2) return null
        val c = g[0] - 'A'
        val r = g.substring(1).toIntOrNull()?.minus(map.rowStart) ?: return null
        if (c !in 0 until map.cols || r !in 0 until map.rows) return null
        return c to r
    }
    fun gridLabel(map: MapDef, col: Int, row: Int) = "${'A' + col}${row + map.rowStart}"

    private var n = 0
    private fun s(map: String, name: String, kind: Kind, grid: String, place: String, detail: String, rarity: String? = null) =
        Spot("s${n++}", map, name, kind, grid, place, detail, rarity)
    private fun k(name: String, grid: String, place: String, detail: String, rarity: String? = null) = s("hajin", name, Kind.KEY, grid, place, detail, rarity)
    private fun kx(map: String, name: String, grid: String, detail: String, rarity: String? = null) = s(map, name, Kind.KEY, grid, "X01", detail, rarity)

    private const val L = "x01_lower"
    private const val U = "x01_upper"

    val spots: List<Spot> = buildList {
        // ------------------------------------------------------------ Hajin key rooms
        add(k("Air Base Access Card (Multi Use)", "B5", "Chang-san Air Base", "Mission item from Iron Citadel. Upgraded at the Admin Complex No. 2 terminal.", "Epic"))
        add(k("Air Base Armory Card (Single Use)", "B6", "Chang-san Air Base Bunker", "Opens the air base armory once.", "Legendary"))
        add(k("Air Base DRC Weapons Locker", "B6", "Chang-san Air Base", "Secure armory deep in the air base bunker, south hillside.", "Ultra"))
        for (g in listOf("F4", "G6", "G7", "G8", "I7")) add(k("Bank Branch Vault Access", g, "Hanex Bank branch", "Keycard for the currency storage vault in a Hanex bank branch. Branches at F4, G6, G7, G8 and I7.", "Rare"))
        add(k("Black Market Weapons Locker", "B7", "Deadtown", "Weapons locker in the basement back room of the black market, south Deadtown. Also opens the one in Hanrim Village.", "Rare"))
        add(k("Black Market Weapons Locker", "C2", "Hanrim Village", "Weapons locker in the basement of the black market, east Hanrim Village (far north-west). Also opens the Deadtown one.", "Rare"))
        add(k("Boathouse Key", "I4", "Haneul Nuclear Reactor", "Mission item. Reactor boathouse."))
        add(k("Coin Mining Den", "G6", "NuriGO West", "Grey office door on the 3rd-floor landing of the building east of the NuriGO gas station.", "Rare"))
        add(k("Commandant Office", "B5", "Chang-san Air Base", "Second floor of Admin Complex No. 2, off a hallway with red curtains and flags.", "Epic"))
        add(k("Counting Room Vault", "F4", "Heavenly Luck Casino, Cheongun Village", "Staff area behind the gaming floor, casino main floor.", "Epic"))
        add(k("Design Center Director's Office", "G7", "Hanwoori Design Complex, The Grid", "Top floor of the Hanwoori Design Complex, reached by elevator."))
        add(k("Downtown Branch Lobby Main", "G6", "Hanex Bank, The Grid", "Main lobby of the downtown Hanex branch.", "Uncommon"))
        add(k("Executive Arms Auction", "G6", "DBN Headquarters, The Grid", "Dark teal door in the arms-auction room, top floor of DBN HQ.", "Epic"))
        add(k("Field Depot", "B5", "Chang-san Air Base", "Gear-maintenance room, ground floor of the military supply warehouse, north-east of the clinic. White door with a red panel.", "Rare"))
        add(k("Fire Station Storage", "D8", "Imjin Farmland fire station", "Upstairs storage. Same key opens the Cheongun (E4) and NuriGO (H6) fire stations.", "Uncommon"))
        add(k("Fire Station Storage", "E4", "Cheongun Village fire station", "Upstairs storage. Same key opens the Imjin (D8) and NuriGO (H6) fire stations.", "Uncommon"))
        add(k("Fire Station Storage", "H6", "NuriGO Mall fire station", "Upstairs storage. Same key opens the Imjin (D8) and Cheongun (E4) fire stations.", "Uncommon"))
        add(k("Gangbyeon Apartment A", "G7", "Hajin River Heights", "Glass double doors of Gangbyeon Apartment A."))
        add(k("Gangbyeon Apartment B", "G7", "Compound Echo, Hajin River Heights", "Gangbyeon Apartment B. The key often spawns in Compound Echo itself.", "Uncommon"))
        add(k("Gas Station Electrical Room", "H7", "Construction Zone, The Grid", "Grey metal door beside a noticeboard, basement of the Construction Zone gas station. Some guides list H6.", "Rare"))
        add(k("Gorani's Scout Overlook", "D8", "Rice Mill, Imjin Farmland", "Grey steel door with a red hazard sign at the top of the main Rice Mill building.", "Legendary"))
        add(k("Gun Store Weapons Locker", "E4", "Cheongun Village", "Back room of the gun store in western Cheongun. Also opens the Songdo Wharf store.", "Rare"))
        add(k("Gun Store Weapons Locker", "I6", "Songdo Wharf", "Back room of the gun store in northern Songdo Wharf. Also opens the Cheongun store.", "Rare"))
        add(k("Haejeok Radio Station", "D6", "Imjin Farmland", "Upstairs in the radio station.", "Rare"))
        add(k("Haksan School Armory", "B7", "Deadtown", "Makeshift armory on the school's 2nd floor, north-east of the Re-Education Center.", "Rare"))
        add(k("Hanex Guard Station", "G6", "Hanex Bank, The Grid", "Guard station on an upper floor near the Hanex vault.", "Epic"))
        add(k("Hanwoori Art Records Room", "G7", "Hanwoori Design Complex, The Grid", "First floor of the design complex.", "Rare"))
        add(k("Hazard Storehouse", "I5", "Haneul Nuclear Reactor", "Light-blue door on a white corrugated shed, south shore of the reactor island.", "Rare"))
        add(k("Helicopter Key", "H6", "The Grid / Cheongun Outskirts", "Mission item for a helicopter."))
        add(k("Hoesik Supply Room", "D8", "Rice Mill, Imjin Farmland", "Supply room at the Rice Mill.", "Epic"))
        add(k("Hospital Chemical Lab", "F5", "Mirae General Hospital", "Basement lab.", "Rare"))
        add(k("Hospital Chemical Lockers", "F5", "Mirae General Hospital", "Lockers in the basement lab.", "Rare"))
        add(k("Hotel Lucera Lobby", "H7", "Hotel Lucera", "Hotel Lucera lobby."))
        add(k("Imjin Mill Foreman's Office", "D8", "Rice Mill, Imjin Farmland", "Foreman's office on the first floor of the Rice Mill.", "Rare"))
        add(k("Iskra's Control Room", "F7", "Cheonwang Peak Array", "Upstairs control room (floor 4) in the south broadcast building.", "Legendary"))
        add(k("Jang-ui's Converted Power Room", "H6", "NuriGO Mall", "Enclosed generator room on the lowest floor of the mall, beside the Robo Dog agility course.", "Legendary"))
        add(k("Jin-Tae's Private Lounge", "F4", "Heavenly Luck Casino, Cheongun Village", "Penthouse: take the basement elevator up, then head east.", "Legendary"))
        add(k("Kang's Arms Incinerator", "B3", "14th Political Prison", "Central block of the prison."))
        add(k("Lucera Suite 703 Bedroom", "H7", "Hotel Lucera", "Suite on floor 12 or above.", "Epic"))
        add(k("Mechanic's Shack", "F5", "Cheongun Village", "Small standalone hut south of the Cheongun clinic.", "Rare"))
        add(k("Militia Arms Depot", "G7", "Hajin River Heights", "Armory one flight up in Gangbyeon Apartment B, Compound Echo.", "Rare"))
        add(k("Mill Explosives Lab", "D8", "Rice Mill, Imjin Farmland", "Explosives lab in the Rice Mill. Door not confirmed yet.", "Uncommon"))
        add(k("Mirae Electrical Room", "F5", "Mirae General Hospital", "Grey double door, north-west basement garage, level B2.", "Rare"))
        add(k("Netshin IT Room", "G6", "Netshin Tech Complex, The Grid", "High-security server room, floor 14.", "Epic"))
        add(k("Netshin Security Office", "G6", "Netshin Tech Complex, The Grid", "One floor below the elevator landing.", "Rare"))
        add(k("Old Barber Shop", "I7", "Songdo Wharf", "Barber shop next to the crab shack, south side of the wharf.", "Rare"))
        for (g in listOf("C5", "D3", "F4", "H5")) add(k("Police Station Armory", g, "Police station", "Basement corridor by the holding cells, beside the caged armory window. One key opens any of the four stations (C5, D3, F4, H5).", "Uncommon"))
        add(k("Police Weapon Depository", "G7", "Wolves Stadium", "Police station by Wolves Stadium.", "Epic"))
        add(k("Prison Encampment Storage", "C3", "14th Political Prison", "Storage in the prison encampment.", "Rare"))
        add(k("Rail Depot Storage", "E5", "Cheongun Village", "Basement storeroom of the train station, near the fire station.", "Rare"))
        add(k("Re-Education Center Annex", "B7", "Deadtown", "Wooden door on the ground floor of the main building, by a no-public-access sign.", "Epic"))
        add(k("Reactor 02 Control Room", "H4", "Haneul Nuclear Reactor", "Control room inside the Reactor 02 building. Radiation gear needed. Some guides list I4.", "Rare"))
        add(k("Reactor DRC Locker", "H4", "Haneul Nuclear Reactor", "DRC weapons locker underwater in Reactor 02's irradiated pool.", "Ultra"))
        add(k("Rice Mill Bunker", "D8", "Rice Mill, Imjin Farmland", "Green metal door with a round handle at the Rice Mill."))
        add(k("Roze's Highrise Armory", "G6", "Netshin Tech Complex, The Grid", "Armory on floor 14 of the Netshin high-rise.", "Legendary"))
        add(k("Shop Records Room", "F7", "Wolves Stadium", "Frosted-glass door in a shop building above a supermarket, south of the clinic.", "Rare"))
        add(k("Shopping DRC Locker", "H6", "NuriGO Mall", "DRC Labs beneath the mall, in the secured lower parking garage.", "Ultra"))
        add(k("Silo Control Room", "A6", "Underground Silo Complex", "Locked control room near a stairway by silo 42."))
        add(k("South Block Armory", "B3", "14th Political Prison", "South Block of the Citadel.", "Legendary"))
        add(k("Supreme Leader's Bunker Access Card", "B6", "Chang-san Air Base", "Supreme Leader's Bunker, deep in the air base bunker. Dropped by the Lieutenant Bale.", "Legendary"))
        add(k("The Chemist's Secure Armory", "I4", "Haneul Nuclear Reactor", "Secured bunker near the reactor boathouse.", "Legendary"))
        add(k("The Citadel Main Control Room", "B3", "14th Political Prison", "First floor of the Citadel."))
        add(k("The Citadel Secondary Control Room", "B3", "14th Political Prison", "Second floor of the Citadel."))
        add(k("The Citadel South Wing", "B3", "14th Political Prison", "South wing of the Citadel.", "Uncommon"))
        add(k("The Doctor's Command Office", "F5", "Mirae General Hospital", "Pale wooden door on the upper office floor (floor 7).", "Legendary"))
        add(k("Underground Equipment Storage", "B6", "Deadtown", "Underground storage near Deadtown. Not yet verified."))
        add(k("Underground Propaganda Store Room", "B7", "Re-Education Center, Deadtown", "Basement cell block, off a dark corridor.", "Rare"))
        add(k("Utility Maintenance Storage", "H4", "Haneul Nuclear Reactor", "Maintenance storeroom at the reactor.", "Rare"))
        add(k("Vault Armory", "F4", "Heavenly Luck Casino, Cheongun Village", "Small armory in the casino's underground parking.", "Epic"))
        add(k("Weapons Mod Workshop", "B5", "Chang-san Air Base", "Second floor.", "Rare"))

        // ------------------------------------------------------------ Hajin places
        for (g in listOf("C5", "D3", "F4", "H5")) add(s("hajin", "Police Station", Kind.POLICE, g, "Police station", "Locked armory in the basement (Police Station Armory key). Roof overwatch."))
        for (g in listOf("C6", "D8", "E4", "H6")) add(s("hajin", "Fire Station", Kind.FIRE, g, "Fire station", "Radiation gear and parachutes. Upstairs storage needs the Fire Station Storage key (not C6)."))
        for (g in listOf("F4", "G6", "G7", "G8", "I7")) add(s("hajin", "Hanex Bank Branch", Kind.BANK, g, "Bank branch", "Vault needs the Bank Branch Vault Access keycard."))
        add(s("hajin", "Black Market", Kind.MARKET, "B7", "Deadtown", "South Deadtown. Weapons locker in the basement back room."))
        add(s("hajin", "Black Market", Kind.MARKET, "C2", "Hanrim Village", "East Hanrim Village, far north-west. Weapons locker in the basement."))
        add(s("hajin", "X01 entrance", Kind.ENTRY, "B1", "North-west border", "Square B0 on the in-game tac map: just past the top edge here, straight north of the 14th Political Prison. An abandoned freight elevator; press the button by the gate and stay in the cage. You can also deploy straight into X01 from Select Location."))

        // ------------------------------------------------------------ X01 areas
        add(s(L, "Deep Vault", Kind.AREA, "C2", "X01 lower floor", "Round vault gate (Deep Vault keycards). Elevator down to three levels of keycard rooms. Blix may be here."))
        add(s(L, "Supply Depot", Kind.AREA, "F2", "X01 lower floor", "Keycard Encoder Station: makes Maintenance or Executive Access."))
        add(s(L, "Pump Station", Kind.AREA, "G3", "X01 lower floor", ""))
        add(s(L, "Power Generators", Kind.AREA, "H3", "X01 both floors", "Spans both floors. Emergency gate needs Wheelson's Remote Module."))
        add(s(L, "Tech Lab", Kind.AREA, "E4", "X01 lower floor", "Exfil lift. Small Test Chamber is in front of the security checkpoints."))
        add(s(L, "Bio Lab", Kind.AREA, "F4", "X01 lower floor", ""))
        add(s(L, "Medical Wing", Kind.AREA, "C4", "X01 lower floor", "Emergency exfil shaft."))
        add(s(L, "Production Assembly", Kind.AREA, "F5", "X01 lower floor", "Big central hall. 3D Printer upstairs on the west side; Maintenance Room at the top. Final exfil lift."))
        add(s(L, "Data Center", Kind.AREA, "H5", "X01 lower floor", "Knuckle in the dark server room. Keycard Encoder Station at I5."))
        add(s(U, "Cargo Bay", Kind.AREA, "F2", "X01 upper floor", "Emergency exfil shaft at E2."))
        add(s(U, "Control Room", Kind.AREA, "G3", "X01 upper floor", ""))
        add(s(U, "Power Generators", Kind.AREA, "H3", "X01 both floors", "Spans both floors."))
        add(s(U, "Admin Offices", Kind.AREA, "D5", "X01 upper floor", "Executive Room on the second floor near the statue."))
        add(s(U, "Main Warehouse", Kind.AREA, "F4", "X01 upper floor", "The Captain patrols here and in the Logistics Tunnel. Audit Room up the east stairwell."))
        add(s(U, "Motor Pool", Kind.AREA, "F7", "X01 upper floor", "The Wrench, inside the parked tanks. Exfil lift at F8."))

        // ------------------------------------------------------------ X01 keycards
        add(kx(L, "Deep Vault 301", "C2", "Opens the Deep Vault gate without being used up, then Room 301: red bio lab on vault level 3, past 303 and 302.", "Legendary"))
        add(kx(L, "Deep Vault 106", "D2", "Room 106 on the lab level of the Deep Vault.", "Legendary"))
        add(kx(L, "Deep Vault 406", "C2", "Room 406 inside the Deep Vault."))
        add(kx(L, "X01 Vault DRC Locker", "C2", "DRC locker in the Deep Vault Core Room.", "Ultra"))
        add(kx(L, "Maintenance Room", "E5", "Plain metal door, 3rd floor at the top of the Production Assembly, next to fenced pipework. Encode one at the Supply Depot.", "Rare"))
        add(kx(L, "Small Test Chamber", "F4", "In the Tech Lab, in front of the security checkpoints."))
        add(kx(U, "Executive Room", "C5", "Second floor near the statue in the Admin Offices. Encode one at the Supply Depot."))
        add(kx(U, "Audit Room", "G5", "Up the east stairwell of the Main Warehouse, end of a dark corridor; red no-entry sign. Two weapon lockers and a floor safe. Encode one at the Data Center.", "Epic"))
        add(kx(U, "Superintendent's Office", "E4", "Upper floor."))
        add(kx(U, "Lt. Chol-Min's Stash Room", "E7", "Upper floor."))

        // ------------------------------------------------------------ X01 exits, bosses, stations
        add(s(U, "Emergency exfil shaft (Cargo Bay)", Kind.EXFIL, "E2", "X01 upper floor", "Needs the Staff Emergency Exfil Keycard."))
        add(s(L, "Emergency exfil shaft (Medical Wing)", Kind.EXFIL, "C4", "X01 lower floor", "Needs the Staff Emergency Exfil Keycard."))
        for (m in listOf(L, U)) {
            add(s(m, "Exfil lift (Tech Lab)", Kind.EXFIL, "E4", "X01 both floors", "Call the lift, then ride it out. Normal exfils close as the facility shuts down."))
            add(s(m, "Exfil lift (south of Bio Lab)", Kind.EXFIL, "G5", "X01 both floors", "Call the lift, then ride it out."))
            add(s(m, "Exfil lift (Motor Pool)", Kind.EXFIL, "F8", "X01 both floors", "Call the lift, then ride it out."))
        }
        add(s(L, "Exfil lift (east side)", Kind.EXFIL, "I3", "X01 lower floor", "Call the lift, then ride it out."))
        add(s(L, "Power Generators emergency gate", Kind.EXFIL, "H3", "X01 lower floor", "Needs Wheelson's Remote Module, then a console, power and wheel sequence."))
        add(s(L, "Knuckle", Kind.BOSS, "H5", "Data Center", "In the dark server room. Holds a data keycard."))
        add(s(L, "Blix", Kind.BOSS, "C2", "Deep Vault", "Not in every match."))
        add(s(L, "Wheelson", Kind.BOSS, "H3", "Logistics Tunnel", "Roams the Logistics Tunnel around the edge of the lower floor (pinned where it meets the Power Generators); shows on the tac map when close. Drops the Remote Module."))
        add(s(U, "The Captain", Kind.BOSS, "F4", "Main Warehouse", "Main Warehouse and the Logistics Tunnel."))
        add(s(U, "The Wrench", Kind.BOSS, "F7", "Motor Pool", "Inside the parked tanks."))
        add(s(L, "X01 3D Printer", Kind.STATION, "E5", "Production Assembly", "Upstairs office, west side of the Production Assembly."))
        add(s(L, "Keycard Encoder (Supply Depot)", Kind.STATION, "F2", "Supply Depot", "Encode a blank keycard into Maintenance Access or Executive Access."))
        add(s(L, "Keycard Encoder (Data Center)", Kind.STATION, "I5", "Data Center", "Encode a blank keycard into Engineering Access or Audit Officer Access."))
    }

    val zones = listOf(
        Zone("hajin", "14th Political Prison", 2.0f, 2.5f, 0.85f, Threat.HIGH, "North Korea. The Citadel is Critical."),
        Zone("hajin", "Hanrim Village", 2.5f, 1.5f, 0.42f, Threat.NONE, "Far north-west. Black market."),
        Zone("hajin", "Haneul Nuclear Reactor", 8.0f, 3.8f, 0.95f, Threat.HIGH, "Radiation: bring protection and a gas mask. Border Zone is Medium."),
        Zone("hajin", "Cheongun Village", 4.95f, 3.75f, 0.8f, Threat.LOW, "Heavenly Luck Casino is High. Russian border crossing to the north."),
        Zone("hajin", "Mirae General Hospital", 5.62f, 4.62f, 0.4f, Threat.LOW, "Beside a train station."),
        Zone("hajin", "Chang-san Air Base", 1.6f, 5.0f, 0.95f, Threat.CRITICAL, "North Korea. The bunker is Extreme."),
        Zone("hajin", "Underground Silo Complex", 0.5f, 5.55f, 0.35f, Threat.NONE, ""),
        Zone("hajin", "Deadtown", 1.6f, 6.65f, 0.6f, Threat.LOW, "North Korea. Re-Education Center is Medium. Sewers below."),
        Zone("hajin", "Imjin Farmland", 3.5f, 7.2f, 0.85f, Threat.LOW, "Rice Mill is Medium."),
        Zone("hajin", "NuriGO Mall", 7.3f, 5.0f, 0.6f, Threat.HIGH, "West is High, East is Medium. Tunnels underneath."),
        Zone("hajin", "The Grid", 6.5f, 5.6f, 0.58f, Threat.CRITICAL, "Downtown Hajin City. Netshin Tech Complex is Extreme."),
        Zone("hajin", "Construction Zone", 7.35f, 6.35f, 0.3f, Threat.CRITICAL, "Part of The Grid."),
        Zone("hajin", "Hotel Lucera", 7.75f, 6.8f, 0.24f, Threat.NONE, ""),
        Zone("hajin", "Cheonwang Peak Array", 5.3f, 6.35f, 0.42f, Threat.MEDIUM, "Summit array. Helipad on the roof."),
        Zone("hajin", "Wolves Stadium", 5.95f, 6.8f, 0.4f, Threat.MEDIUM, "Police station and bank nearby."),
        Zone("hajin", "Hajin River Heights", 6.6f, 6.85f, 0.36f, Threat.MEDIUM, "Compound Echo, Gangbyeon apartments."),
        Zone("hajin", "Songdo Wharf", 8.5f, 6.4f, 0.6f, Threat.LOW, "East coast."),
    )

    /** Everything searchable on a map, plus zones as searchable places. */
    data class Hit(val spot: Spot?, val zone: Zone?) {
        val map get() = spot?.map ?: zone!!.map
        val name get() = spot?.name ?: zone!!.name
    }

    fun search(q: String): List<Hit> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        val words = t.split(Regex("\\s+"))
        fun score(text: String, extra: String): Int {
            val a = text.lowercase(); val b = extra.lowercase()
            if (!words.all { a.contains(it) || b.contains(it) }) return -1
            return when {
                a.startsWith(t) -> 0
                a.contains(t) -> 1
                words.all { a.contains(it) } -> 2
                else -> 3
            }
        }
        val out = ArrayList<Pair<Int, Hit>>()
        for (z in zones) score(z.name, z.note).takeIf { it >= 0 }?.let { out.add(it to Hit(null, z)) }
        for (sp in spots) score(sp.name, "${sp.place} ${sp.grid} ${sp.kind.label}").takeIf { it >= 0 }?.let { out.add(it + 1 to Hit(sp, null)) }
        return out.sortedWith(compareBy({ it.first }, { it.second.name })).map { it.second }.take(60)
    }
}
