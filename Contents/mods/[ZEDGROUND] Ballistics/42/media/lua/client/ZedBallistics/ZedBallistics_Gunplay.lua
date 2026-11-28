-- [ZEDGROUND] Ballistics - ADS personal settings lock.
--
-- Project Viewpoint: Recoil & ADS lets every player scale or switch off sway and recoil in their own options
-- (sway, swayAmount 0-2, cameraRecoil, recoilAmount 0-2). With the sandbox option ForceAdsSettings on, the
-- values ADS hands to its Java side are replaced by the fixed ones below, so in PvP nobody shoots without
-- sway or recoil. How strong sway and recoil are is then decided by the sandbox gunplay options
-- (Java: t3zedballistics.gunplay.AdsGunplay).
--
-- ADS's Lua calls the global PVADS_setSettings(table) whenever its options are applied; it is wrapped once,
-- after ADS bound its Java side, and ADS's options are applied again so the lock takes effect at once.

require "ZedBallistics/ZedBallistics_Core"

local wrapped = false
local tries = 0

local function forced()
    return SandboxVars.ZedBallistics.ForceAdsSettings == true
end

local function lock(t)
    t.sway = true
    t.swayAmount = 1.0
    t.cameraRecoil = true
    t.recoilAmount = 1.0
    t.steady = true
    t.hipSpread = false
end

local function tryWrap()
    if wrapped then return true end
    if type(PVADS) ~= "table" or type(PVADS_setSettings) ~= "function" then return false end
    local original = PVADS_setSettings
    PVADS_setSettings = function(t)
        if type(t) == "table" and forced() then lock(t) end
        return original(t)
    end
    wrapped = true
    print("[ZedBallistics] ADS personal settings lock installed (ForceAdsSettings=" .. tostring(forced()) .. ")")
    if type(PVADS.applySettings) == "function" then
        local ok, err = pcall(PVADS.applySettings)
        if not ok then print("[ZedBallistics] re-applying ADS settings failed: " .. tostring(err)) end
    end
    return true
end

local function onTick()
    tries = tries + 1
    if tryWrap() then
        Events.OnTick.Remove(onTick)
    elseif tries >= 1800 then -- about 30 seconds
        print("[ZedBallistics] Project Viewpoint: Recoil & ADS not found; ADS personal settings lock not installed")
        Events.OnTick.Remove(onTick)
    end
end

Events.OnGameStart.Add(function()
    if not tryWrap() then Events.OnTick.Add(onTick) end
end)
