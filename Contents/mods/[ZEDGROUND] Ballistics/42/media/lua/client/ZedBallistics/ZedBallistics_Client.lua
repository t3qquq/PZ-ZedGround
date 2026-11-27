-- [ZEDGROUND] Ballistics - client glue.
-- Port of Viewpoint True Ballistics 1.4.6 (by Matthew) to Project Zomboid 42.22.
--
-- In multiplayer, rounds may only fly further than vanilla's weapon range once the server confirms it
-- runs the True Ballistics Java component (otherwise vanilla anti-cheat would reject - or kick for -
-- long hits). Until then the Java side limits hits to the weapon's MaxRange.
--
-- Developer console:
--   ZGB_status()                         what is active
--   ZGB_describeHeldWeapon()             resolved ballistic profile of the gun in hand
--   ZGB_debug("trajectories", true)      3D flight paths / camera ray / muzzle (also "impacts", "network", "verbose", "all")
--   ZGB_tracers(5)                       every 5th round is a visible tracer (0 = off)
--   ZGB_stress(250)                      250 inert test rounds; then ZGB_stats()
--   ZGB_effects()                        bullet holes / impact debris settings; ZGB_effects("holes", 800),
--                                        ZGB_effects("minutes", 60), ZGB_effects("debris", 1.5), "on", "off", "clear"

require "ZedBallistics/ZedBallistics_Core"

local helloTries = 0
local helloTicks = 0
local confirmed = false

local function sendHello()
    local player = getPlayer()
    if not player then return end
    helloTries = helloTries + 1
    sendClientCommand(player, "ZedBallistics", "hello", { version = ZedBallistics.version })
end

local function onTick()
    if confirmed or not isClient() then
        Events.OnTick.Remove(onTick)
        return
    end
    helloTicks = helloTicks + 1
    if helloTicks >= 300 then -- roughly every 5 seconds
        helloTicks = 0
        if helloTries >= 6 then
            print("[ZedBallistics] server did not answer; it probably does not run True Ballistics' Java part. Hits stay within vanilla weapon range.")
            Events.OnTick.Remove(onTick)
            return
        end
        sendHello()
    end
end

local function onGameStart()
    if ZGB_onGameStart then ZGB_onGameStart() end
    confirmed = false
    helloTries = 0
    helloTicks = 0
    if isClient() then
        sendHello()
        Events.OnTick.Add(onTick)
    end
end

-- Bullet holes / impact debris of other players (relayed by the server) and our own for them.
local function sendImpacts()
    if not isClient() or not ZGB_takeImpacts then return end
    local d = ZGB_takeImpacts()
    if d then
        local player = getPlayer()
        if player then sendClientCommand(player, "ZedBallistics", "fx", { d = d }) end
    end
end
Events.OnTick.Add(sendImpacts)

local function onServerCommand(module, command, args)
    if module ~= "ZedBallistics" then return end
    if command == "fx" then
        if ZGB_remoteImpacts and args then ZGB_remoteImpacts(args.d) end
        return
    end
    if command ~= "hello" then return end
    confirmed = true
    print("[ZedBallistics] server answered: java=" .. tostring(args ~= nil and args.java) .. " version=" .. tostring(args and args.version))
    local java = args ~= nil and args.java == true
    local version = (args ~= nil and args.version) or ""
    if ZGB_setServerConfirmed then ZGB_setServerConfirmed(java, version) end
end

Events.OnGameStart.Add(onGameStart)
Events.OnServerCommand.Add(onServerCommand)

-- Multiplayer ammo guard. The server only takes a round out of the magazine when the shot's hit packet
-- arrives, and True Ballistics sends that packet when the round lands (up to a few tenths of a second
-- later). Without this guard, fast fire at the end of a magazine could outrun the server's count and trip
-- vanilla's "not enough ammo" anti-cheat. It only refuses shots the magazine cannot cover.
local function installAmmoGuard()
    if not ISReloadWeaponAction or not ISReloadWeaponAction.canShoot or ISReloadWeaponAction.ZedBallistics_guarded then return end
    local original = ISReloadWeaponAction.canShoot
    ISReloadWeaponAction.canShoot = function(player, weapon)
        local ok = original(player, weapon)
        if not ok or not isClient() then return ok end
        if player:isUnlimitedAmmo() then return ok end
        local pending = ZGB_pendingRounds()
        if not pending or pending <= 0 then return ok end
        local available = weapon:getCurrentAmmoCount()
        if weapon:haveChamber() and weapon:isRoundChambered() then available = available + 1 end
        if pending >= available then return false end
        return ok
    end
    ISReloadWeaponAction.ZedBallistics_guarded = true
end

Events.OnGameStart.Add(installAmmoGuard)
