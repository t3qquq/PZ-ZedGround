-- ZEDGROUND HUD : 미니맵·큰 지도(M)를 □(북쪽이 위인 평면)로 시작하게 한다
-- 바닐라 기본값은 ◇(Isometric 켜짐)이다: WorldMapSettings 의 MiniMap.Isometric / WorldMap.Isometric 기본 true
-- (zombie.worldMap.WorldMapSettings, 저장 파일은 접속한 세이브 폴더의 InGameMap.ini).
-- 캐릭터마다 처음 한 번만 끈다. 그 뒤 플레이어가 미니맵 톱니바퀴나 큰 지도에서 ◇ 로 바꾸면 바닐라가 그 값을
-- 저장·복원하므로 그대로 둔다 (표시: 플레이어 modData ZedHUDMapFlat).
-- 미니맵은 OnGameStart 직후 만들어지므로(ISMiniMap.InitPlayer) 생길 때까지 최대 MAX_WAIT_MS 기다린다.
local MOD_KEY = "ZedHUDMapFlat"
local MAX_WAIT_MS = 10000

local _startAt = nil

local function log(msg)
    if ZedHUD and ZedHUD.log then ZedHUD.log("map flat: " .. msg) else print("[ZedHUD] map flat: " .. msg) end
end

local function apply(p, why)
    local okS, errS = pcall(function()
        local s = WorldMapSettings.getInstance()
        s:setBoolean("MiniMap.Isometric", false)
        s:setBoolean("WorldMap.Isometric", false)
        s:save()
    end)
    local mini = "none"
    local mm = getPlayerMiniMap(0)
    if mm and mm.inner and mm.inner.mapAPI then
        local ok = pcall(function() mm.inner.mapAPI:setBoolean("Isometric", false) end)
        mini = ok and "flat" or "failed"
    end
    local big = "not open"
    if ISWorldMap_instance then
        local ok = pcall(function() ISWorldMap_instance:setIsometric(false) end)
        big = ok and "flat" or "failed"
    end
    p:getModData()[MOD_KEY] = 1
    log(string.format("applied (%s): settings ok=%s minimap=%s worldmap=%s%s",
        tostring(why), tostring(okS), mini, big, okS and "" or (" err=" .. tostring(errS))))
end

local function onTick()
    local p = getPlayer()
    if not p then return end
    if p:getModData()[MOD_KEY] then
        log("already set for this character, keeping the player's own choice")
        Events.OnTick.Remove(onTick)
        return
    end
    local now = getTimestampMs()
    _startAt = _startAt or now
    local mm = getPlayerMiniMap(0)
    if mm or now - _startAt > MAX_WAIT_MS then
        apply(p, mm and "minimap ready" or "no minimap after wait")
        Events.OnTick.Remove(onTick)
    end
end

local function onGameStart()
    _startAt = nil
    Events.OnTick.Add(onTick)
end

Events.OnGameStart.Add(onGameStart)
