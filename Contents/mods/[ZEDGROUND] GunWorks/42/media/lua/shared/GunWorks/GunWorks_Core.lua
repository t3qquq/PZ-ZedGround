-- [ZEDGROUND] GunWorks - shared Lua API (loaded on clients and on the server).
-- Port of Viewpoint True Ballistics 1.4.6 (by Matthew) to Project Zomboid 42.22.
--
-- Compatibility packs register ballistic values here. Put registrations in a *shared* Lua file so the
-- server and every client use identical profiles (the server validates hit distances with them).
--
--   require "GunWorks/GunWorks_Core"
--   GunWorks.registerAmmoProfile("Base.Bullets9mm", { muzzleVelocity = 370 })
--   GunWorks.registerWeaponProfile("Base.HuntingRifle", { muzzleVelocity = 820, penetration = 2 })
--
-- Keys (all optional): muzzleVelocity (m/s), gravityScale, drag (1/m), projectileMass (g), penetration
-- (bodies), damageMultiplier, maximumRange (m), projectileLifetime (s), pelletCount, spread (pellet
-- cone half-angle, degrees), baseSpread (mrad), tracer (true/false), category (text).

GunWorks = GunWorks or {}
GunWorks.version = "1.4.6-zg3"

-- Java bridge. The Java part is exposed by ZombieBuddy as the table GunWorksJ; it is nil when the
-- Java part is not running (ZombieBuddy missing, or the jar was not approved). The ZGB_* globals below
-- forward to it and do nothing (return nil) without it.
local function J() return GunWorksJ end

function ZGB_version() local j = J(); if j then return j.version() end end
function ZGB_available() local j = J(); if j then return j.available() end end
function ZGB_registerWeaponProfile(fullType, values) local j = J(); if j then j.registerWeaponProfile(fullType, values) end end
function ZGB_registerAmmoProfile(ammo, values) local j = J(); if j then j.registerAmmoProfile(ammo, values) end end
function ZGB_describeHeldWeapon() local j = J(); if j then return j.describeHeldWeapon() end end
function ZGB_setZero(meters) local j = J(); if j then j.setZero(meters) end end
function ZGB_getZero() local j = J(); if j then return j.getZero() end end
function ZGB_debug(what, on) local j = J(); if j then return j.debug(what, on) end end
function ZGB_tracers(every) local j = J(); if j then j.tracers(every) end end
function ZGB_stress(count) local j = J(); if j then return j.stress(count) end end
function ZGB_pendingRounds() local j = J(); if j then return j.pendingRounds() end end
function ZGB_stats() local j = J(); if j then return j.stats() end end
function ZGB_status() local j = J(); if j then return j.status() end; return "True Ballistics Java part is NOT running" end
function ZGB_setServerConfirmed(confirmed, version) local j = J(); if j then j.setServerConfirmed(confirmed, version) end end
function ZGB_serverInfo() local j = J(); if j then return j.serverInfo() end end
function ZGB_onGameStart() local j = J(); if j then j.onGameStart() end end
-- Bullet holes / impact debris (client presentation; relayed between players by ZGB_Client/ZGB_Server).
function ZGB_takeImpacts() local j = J(); if j then return j.takeImpacts() end end
function ZGB_remoteImpacts(d) local j = J(); if j then j.remoteImpacts(d) end end
function ZGB_effects(what, value) local j = J(); if j then return j.effects(what, value) end end
-- NPC mods: real projectiles for scripted shooters (see docs/README.md, "NPC mods").
function ZGB_npcFire(shooter, target, aimX, aimY, aimZ, weapon, hitChance, maxDistance, authoritative, tracer)
    local j = J(); if j then return j.npcFire(shooter, target, aimX, aimY, aimZ, weapon, hitChance, maxDistance, authoritative, tracer) end
end
function ZGB_npcTick() local j = J(); if j then return j.npcTick() end end
function ZGB_npcVisual(launch) local j = J(); if j then j.npcVisual(launch) end end

local queued = {}

local function javaReady()
    return J() ~= nil
end

local function flush()
    if not javaReady() then return end
    for i = 1, #queued do
        local q = queued[i]
        if q.kind == "weapon" then ZGB_registerWeaponProfile(q.key, q.values)
        else ZGB_registerAmmoProfile(q.key, q.values) end
    end
    queued = {}
end

function GunWorks.registerWeaponProfile(fullType, values)
    if type(fullType) ~= "string" or type(values) ~= "table" then
        print("[GunWorks] GunWorks.registerWeaponProfile(fullType, table) expected")
        return
    end
    if javaReady() then ZGB_registerWeaponProfile(fullType, values)
    else table.insert(queued, { kind = "weapon", key = fullType, values = values }) end
end

function GunWorks.registerAmmoProfile(ammo, values)
    if type(ammo) ~= "string" or type(values) ~= "table" then
        print("[GunWorks] GunWorks.registerAmmoProfile(ammo, table) expected")
        return
    end
    if javaReady() then ZGB_registerAmmoProfile(ammo, values)
    else table.insert(queued, { kind = "ammo", key = ammo, values = values }) end
end

-- For ADS / scope mods: zero distance of the sight in use (metres); a negative value restores the default.
function GunWorks.setZero(meters)
    ZGB_setZero(meters)
end

function GunWorks.getZero()
    return ZGB_getZero() or 0
end

function GunWorks.isAvailable()
    return J() ~= nil
end

Events.OnGameBoot.Add(flush)
Events.OnGameStart.Add(flush)
if Events.OnServerStarted then Events.OnServerStarted.Add(flush) end
