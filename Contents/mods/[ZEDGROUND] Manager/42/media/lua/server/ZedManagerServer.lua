-- Manager 서버: 판 상태 보관·방송
--
-- 뼈대 단계 (11-18). 다음 단계에서 이 파일에 붙일 것
--   캐릭터 초기화 (인벤토리·부상·생존 수치), 사망 처리 (오일리그 리스폰), 판 종료 시 생존자 대기실 이동

if isClient() then return end

require "ZedManagerConfig"

local RM = ZedManager
local state = RM.STATE.WAITING

local function log(msg) RM.log(msg) end

local function toClients(cmd, args)
    if isServer() then
        sendServerCommand(RM.MODULE, cmd, args)
    elseif RM.onServerCommand then
        RM.onServerCommand(RM.MODULE, cmd, args)
    end
end

local function toPlayer(p, cmd, args)
    if isServer() then
        sendServerCommand(p, RM.MODULE, cmd, args)
    elseif RM.onServerCommand then
        RM.onServerCommand(RM.MODULE, cmd, args)
    end
end

local function isAdminPlayer(p)
    if not isServer() then return true end   -- SP
    return p:isAccessLevel("admin")
end

-- 다른 서버 코드(사망 처리 등)가 읽는다
function RM.getState()
    return state
end

local function setState(s, who)
    if s == state then return end
    log("state " .. state .. " -> " .. s .. " by " .. tostring(who))
    state = s
    toClients("State", { state = state })
end

-- ── 라운드 시작 / 종료 (11-22) ─────────────────────────────
-- 라운드 시작 한 번이면: 판 상태 진행 중 -> 공수 강하 시작(경로 준비 포함) -> 수송기가 출발하는 순간 봉쇄 구역 시작.
--   봉쇄 구역은 배그처럼 비행기 출발과 함께 시작한다 (시작 파란 원이 영역 전체라 공중·탑승 중 데미지 없음, 1차 대기가 길다).
--   공수 강하가 꺼져 있으면 봉쇄 구역만 바로 시작한다.
-- 라운드 종료: 공수 강하·봉쇄 구역을 끄고 판 상태 대기.
-- 각 모드는 공개 진입점(HaloDrop.Server / BlueZone.Server)만 부른다. 모드가 없으면 그 단계만 건너뛴다.
local round = nil          -- { by, blueStarted } 라운드 시작으로 연 판
local hooked = false

local function haloApi() return HaloDrop and HaloDrop.Server end
local function blueApi() return BlueZone and BlueZone.Server end

local function startBlue(why)
    local bz = blueApi()
    if not bz then
        log("round: blue zone mod off -> skipped (" .. tostring(why) .. ")")
        return
    end
    if bz.running() then
        log("round: blue zone already running -> left as is (" .. tostring(why) .. ")")
    else
        bz.start("ZEDGROUND round (" .. tostring(round and round.by) .. ")")
        log("round: blue zone started (" .. tostring(why) .. ")")
    end
    if round then round.blueStarted = true end
end

local function onHaloEvent(ev, info)
    if not round then return end
    if ev == "launch" and not round.blueStarted then
        startBlue("plane launched, round " .. tostring(info and info.id) .. " members=" .. tostring(info and info.members))
    elseif ev == "closed" then
        log("round: halo drop round " .. tostring(info and info.id) .. " closed (" .. tostring(info and info.reason) .. ")")
    end
end

local function notice(p, key)
    toPlayer(p, "Notice", { key = key })
end

local function roundStart(p, who)
    local hd, bz = haloApi(), blueApi()
    if not hd and not bz then
        log("RoundStart ignored: neither HALO Drop nor Blue Zone is active")
        notice(p, "IGUI_ZedManager_Round_NoMods")
        return
    end
    if round or (hd and hd.phase() ~= "idle") or (bz and bz.running()) then
        log(string.format("RoundStart ignored: already running (managed=%s halo=%s blue=%s)",
            tostring(round ~= nil), hd and hd.phase() or "off", tostring(bz and bz.running())))
        notice(p, "IGUI_ZedManager_Round_Busy")
        return
    end
    round = { by = who, blueStarted = false }
    setState(RM.STATE.RUNNING, who .. " (round start)")
    log(string.format("round: start by %s (halo=%s blue=%s)", who, tostring(hd ~= nil), tostring(bz ~= nil)))
    if hd then
        if not hooked then
            hd.onEvent(onHaloEvent)
            hooked = true
        end
        if not hd.start(p) then
            log("round: halo drop start FAILED -> round cancelled")
            round = nil
            setState(RM.STATE.WAITING, "round start failed")
            notice(p, "IGUI_ZedManager_Round_Busy")
            return
        end
        -- 경로 준비 없이 바로 출발한 경우 launch 이벤트가 start 안에서 이미 왔다 (blueStarted = true)
        if not round.blueStarted then log("round: waiting for the plane to launch (route prep)") end
    else
        startBlue("halo drop mod off")
    end
end

local function roundStop(who)
    local hd, bz = haloApi(), blueApi()
    log(string.format("round: stop by %s (managed=%s halo=%s blue=%s)",
        who, tostring(round ~= nil), hd and hd.phase() or "off", tostring(bz and bz.running())))
    round = nil
    if hd and hd.phase() ~= "idle" then hd.stop("ZEDGROUND round stop by " .. who) end
    if bz and bz.running() then bz.stop("ZEDGROUND round stop by " .. who) end
    setState(RM.STATE.WAITING, who .. " (round stop)")
end

local function onClientCommand(module, command, player, args)
    if module ~= RM.MODULE then return end
    local who = tostring(player and player:getUsername())
    if command == "SetState" then
        if not isAdminPlayer(player) then
            log("SetState denied for " .. who)
            return
        end
        local s = args and args.state
        if s ~= RM.STATE.WAITING and s ~= RM.STATE.RUNNING then
            log("SetState ignored: bad state '" .. tostring(s) .. "' from " .. who)
            return
        end
        setState(s, who)
    elseif command == "RoundStart" or command == "RoundStop" then
        if not isAdminPlayer(player) then
            log(command .. " denied for " .. who)
            return
        end
        local ok, err = pcall(function()
            if command == "RoundStart" then roundStart(player, who) else roundStop(who) end
        end)
        if not ok then log(command .. " FAILED err=" .. tostring(err)) end
    elseif command == "Sync" then
        log("sync from " .. who .. " -> " .. state)
        toPlayer(player, "State", { state = state })
    elseif command == "ViewStatus" then
        -- 시점 고정 결과 (ZedManagerView.lua). 내보내기는 클라가 스스로 한다: 여기서는 기록만
        local a = args or {}
        log("view " .. (a.ok and "ok" or "FAILED") .. " for " .. who
            .. ": " .. tostring(a.reason) .. " after " .. tostring(a.ms) .. " ms")
    end
end
Events.OnClientCommand.Add(onClientCommand)
log("server ready, state " .. state)
