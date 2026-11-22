-- Round Manager 클라: 시점 고정 (아이소 배제, Viewpoint 1·3인칭 전용 서버)
--
-- 샌드박스 RoundManager.ViewLock 이 켜져 있으면
--   1. 접속 직후부터 Viewpoint 를 1인칭(또는 3인칭)으로 붙잡는다. 붙잡는 일은 클라 자바가 한다
--      (media/java/client/RoundManagerView.jar, ZombieBuddy 가 서버 접속 때 로드. 소스 42/src/t3roundview).
--      Lua 는 전역 테이블 RoundManager.VIEW 에 원하는 값을 적고, 자바가 같은 테이블에 현재 상태를 적는다.
--        Lua -> 자바   lock (boolean), mode ("first" | "third")
--        자바 -> Lua   jar (버전), enabled (Viewpoint 가 월드를 그리는 중 = 1·3인칭), third, at (마지막 프레임 ms)
--   2. O 키는 아이소 전환이 아니라 1인칭 <-> 3인칭 전환이 된다 (고른 값은 캐릭터 modData 에 남긴다).
--   3. 1·3인칭이 될 때까지 화면을 검게 가린다 (아이소 화면을 보여주지 않는다).
--   4. 1·3인칭이 불가능하면 안내를 보여주고 메인 메뉴로 내보낸다. 관리자·싱글은 내보내지 않는다(로그만).
--        no_viewpoint  전역 Viewpoint 가 없다 (ZombieBuddy 또는 Viewpoint 자바가 안 떴다)
--        no_jar        자바 쪽 신호(at)가 없다 (ZombieBuddy 승인 창에서 이 모드 jar 를 거절했거나 Viewpoint 이름이 바뀜)
--        not_enabled   신호는 오는데 끝내 1·3인칭이 안 됐다
--      결과는 서버에도 알린다: C -> S ViewStatus { ok, reason, ms }
-- 완전히 접속 전에 막을 수는 없다: 서버는 클라 자바가 떴는지 접속 단계에서 알 수 없다.

if isServer() then return end

require "RoundManagerConfig"
require "ISUI/ISUIElement"

local RM = RoundManager
RM.VIEW = RM.VIEW or { lock = false, mode = "first" }
local V = RM.VIEW

local CFG = {
    TOGGLE_KEY      = Keyboard.KEY_O,
    LOCK_REFRESH_MS = 1000,    -- 샌드박스 값을 다시 읽는 간격 (관리자가 판 중에 바꿔도 반영)
    STALE_MS        = 2000,    -- 자바 신호가 이보다 오래되면 끊긴 것으로 본다
    COVER_AGAIN_MS  = 1500,    -- 한 번 1·3인칭이 된 뒤에는 이만큼 연속으로 안 될 때만 다시 가린다 (전환 깜빡임 무시)
    NO_VIEWPOINT_MS = 5000,
    NO_JAR_MS       = 15000,
    NOT_ENABLED_MS  = 90000,   -- 첫 접속은 Viewpoint 가 셰이더·월드를 준비하느라 오래 걸릴 수 있다
    EXIT_NOTICE_MS  = 12000,   -- 안내를 보여주는 시간
}

local function log(msg) RM.log("view: " .. tostring(msg)) end

-- ── 가림막 ───────────────────────────────────────────────
local Cover = ISUIElement:derive("RoundManagerViewCover")

function Cover:new()
    local core = getCore()
    local o = ISUIElement:new(0, 0, core:getScreenWidth(), core:getScreenHeight())
    setmetatable(o, self)
    self.__index = self
    o.lines = {}
    return o
end

function Cover:render()
    local core = getCore()
    local w, h = core:getScreenWidth(), core:getScreenHeight()
    if self.width ~= w then self:setWidth(w) end
    if self.height ~= h then self:setHeight(h) end
    self:drawRect(0, 0, w, h, 1, 0, 0, 0)
    local fontHgt = getTextManager():getFontFromEnum(UIFont.Large):getLineHeight()
    local y = h / 2 - (#self.lines * (fontHgt + 6)) / 2
    for i = 1, #self.lines do
        self:drawTextCentre(self.lines[i], w / 2, y, 1, 1, 1, 1, UIFont.Large)
        y = y + fontHgt + 6
    end
end

local cover = nil
local coverOnTop = false

local function showCover(lines, onTop)
    if not cover then
        cover = Cover:new()
        cover:initialise()
        cover:addToUIManager()
        cover:backMost()          -- 준비 중에는 다른 UI 뒤에 (월드만 가린다)
        coverOnTop = false
        log("cover shown")
    end
    cover.lines = lines
    if onTop and not coverOnTop then
        coverOnTop = true         -- 내보내기 안내는 다른 창에 가리지 않게 맨 위로
        cover:setAlwaysOnTop(true)
        cover:bringToTop()
    end
end

local function hideCover()
    if not cover then return end
    cover:removeFromUIManager()
    cover = nil
    coverOnTop = false
    log("cover hidden")
end

-- ── 판정 ─────────────────────────────────────────────────
local startAt = nil        -- OnGameStart 시각
local nextLockRead = 0
local notOkSince = nil     -- 1·3인칭이 아닌 상태가 이어지기 시작한 시각
local everOk = false
local exempt = false       -- 관리자·싱글: 실패해도 내보내지 않는다
local exitAt, exitReason = nil, nil
local exiting = false

local function isExempt(p)
    if not isClient() then return true end   -- SP
    return p:isAccessLevel("admin")
end

local function report(p, ok, reason, ms)
    sendClientCommand(p, RM.MODULE, "ViewStatus", { ok = ok, reason = reason, ms = ms })
end

local function leaveGame()
    if exiting then return end
    exiting = true
    log("leaving to the main menu (" .. tostring(exitReason) .. ")")
    -- 바닐라 인게임 "나가기" 와 같은 순서 (MainScreen.lua EXIT)
    setGameSpeed(1)
    pauseSoundAndMusic()
    setShowPausedMessage(true)
    getCore():quit()
end

local function fail(p, reason, now)
    if isExempt(p) then
        if not exempt then
            exempt = true
            log("FAILED (" .. reason .. ") but player is admin or singleplayer: staying, cover off")
            report(p, false, reason .. " (exempt)", now - startAt)
        end
        hideCover()
        return
    end
    if exitAt then return end
    exitAt = now + CFG.EXIT_NOTICE_MS
    exitReason = reason
    log("FAILED (" .. reason .. "): leaving in " .. CFG.EXIT_NOTICE_MS .. " ms")
    report(p, false, reason, now - startAt)
end

local REASON_TEXT = {
    no_viewpoint = "IGUI_RoundManager_View_NoViewpoint",
    no_jar       = "IGUI_RoundManager_View_NoJar",
    not_enabled  = "IGUI_RoundManager_View_NotEnabled",
}

local function onTick()
    if not startAt or exiting then return end
    local now = getTimestampMs()

    if now >= nextLockRead then
        nextLockRead = now + CFG.LOCK_REFRESH_MS
        local lock = SandboxVars.RoundManager.ViewLock
        if lock ~= V.lock then
            log("lock " .. tostring(V.lock) .. " -> " .. tostring(lock) .. " (sandbox RoundManager.ViewLock)")
            V.lock = lock
            notOkSince, exempt, exitAt, exitReason = nil, false, nil, nil
        end
    end
    if not V.lock then
        hideCover()
        return
    end

    if exitAt then
        local left = math.ceil((exitAt - now) / 1000)
        if left <= 0 then
            leaveGame()
            return
        end
        showCover({
            getText("IGUI_RoundManager_View_Title"),
            getText(REASON_TEXT[exitReason]),
            getText("IGUI_RoundManager_View_HowTo"),
            getText("IGUI_RoundManager_View_ExitIn", tostring(left)),
        }, true)
        return
    end

    local p = getPlayer()
    if not p or p:isDead() then
        notOkSince = nil
        hideCover()
        return
    end
    if exempt then return end

    local alive = V.at ~= nil and (now - V.at) < CFG.STALE_MS
    if alive and V.enabled == true then
        if not everOk then
            everOk = true
            log("ok: " .. (V.third and "third" or "first") .. " person after " .. (now - startAt) .. " ms (jar " .. tostring(V.jar) .. ")")
            report(p, true, V.third and "third" or "first", now - startAt)
        end
        notOkSince = nil
        hideCover()
        return
    end

    notOkSince = notOkSince or now
    local span = now - notOkSince
    if not everOk or span >= CFG.COVER_AGAIN_MS then
        showCover({ getText("IGUI_RoundManager_View_Title"), getText("IGUI_RoundManager_View_Wait") }, false)
    end
    if Viewpoint == nil and span >= CFG.NO_VIEWPOINT_MS then
        fail(p, "no_viewpoint", now)
    elseif not alive and span >= CFG.NO_JAR_MS then
        fail(p, "no_jar", now)
    elseif span >= CFG.NOT_ENABLED_MS then
        fail(p, "not_enabled", now)
    end
end

-- ── O 키: 1인칭 <-> 3인칭 ────────────────────────────────
local function onKeyPressed(key)
    if key ~= CFG.TOGGLE_KEY or not V.lock or not startAt then return end
    local p = getPlayer()
    if not p or p:isDead() then return end
    V.mode = (V.mode == "third") and "first" or "third"
    p:getModData().RoundManagerViewMode = V.mode
    log("key O: mode -> " .. V.mode)
end

local function onGameStart()
    local p = getPlayer()
    local saved = p and p:getModData().RoundManagerViewMode
    if saved == "first" or saved == "third" then V.mode = saved end
    V.lock = SandboxVars.RoundManager.ViewLock
    startAt = getTimestampMs()
    log("start: lock=" .. tostring(V.lock) .. " mode=" .. V.mode
        .. " Viewpoint=" .. tostring(Viewpoint ~= nil) .. " jar=" .. tostring(V.jar))
end

Events.OnGameStart.Add(onGameStart)
Events.OnTick.Add(onTick)
Events.OnKeyPressed.Add(onKeyPressed)
