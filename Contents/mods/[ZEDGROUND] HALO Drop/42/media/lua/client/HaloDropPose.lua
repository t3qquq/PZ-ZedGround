-- Halo Drop: 강하 자세 (플레이어 애니메이션 변수)
--
-- 강하 중인 플레이어에게 애니메이션 변수 HaloDropPose 를 세운다. 값에 따라
-- AnimSets/player/<상태>/HaloDropPose_*.xml 노드가 고른다.
--   "freefall" = 자유낙하 2D 블렌드 (HaloDropPose_Freefall.xml). 변수 HaloDropFFX / HaloDropFFY 로 섞는다.
--   "canopy"   = 낙하산 2D 블렌드 (HaloDropPose_Canopy.xml). 변수 HaloDropCPX / HaloDropCPY 로 섞는다.
--   nil        = 변수 해제 (바닐라 애니메이션으로 복귀)
--
-- 2D 블렌드 자세 배치 (claude/PUBG_강하_조작명세서.md 5절, 배그 녹화 11-16 기준)
--   자유낙하 X(-1 왼쪽 기울기 ~ +1 오른쪽 기울기) x Y(-1 ~ 2)
--     Y -1 감속(S): 팔다리를 크게 벌린 X 자, 다리를 아래로 내림          HaloDropFF_Brake(L/R)
--     Y  0 무입력: 엎드림, 무릎 굽혀 발바닥이 위, 팔은 몸 옆             HaloDropFreefall, HaloDropFF_Neutral(L/R)
--     Y  1 W 활공: 머리 쪽으로 약 35도 숙임, 다리 모음, 팔 옆으로 벌림    HaloDropGlide, HaloDropFF_Glide(L/R)
--     Y  2 다이브: 머리부터 수직, 팔다리 붙임                         HaloDropDive (가운데 하나)
--     X +-1: 몸을 그쪽으로 30도 롤. 무입력·감속 줄은 위로 올라간 쪽 무릎을 더 굽히고 내려간 쪽 다리를 편다
--   낙하산 X(-1 왼쪽 선회 ~ +1 오른쪽 선회) x Y(-1 플레어 ~ +1 몸 뒤로), 5 x 3 = 15 클립 (11-23 그네 개편)
--     바닥: 바닐라 Bob_FallIdle(0.8배속, 팔을 든 Y자 - 퐁듀 좀비 공습과 같음) = HaloDropCP_C
--     몸 전체를 조종줄 잡는 손 높이(모델 y 0.92) 기준으로 돌린다. 머리가 거의 안 움직여(카메라는 머리 뼈를 따라감)
--     화면에서 캐노피는 선회 쪽으로 기울고 몸은 바깥으로 흔들려 나간다 (낙하산은 Bip01_Spine1 에 묶여 같이 돈다).
--     X: -1 / -0.5 / 0 / 0.5 / 1 = 롤 -35 / -17.5 / 0 / 17.5 / 35도 (11-23 저녁 70 -> 35).  +-0.5 부터 선회 쪽 손이 조종줄을 가슴 옆까지 당긴다
--        (손이 몸보다 먼저 다 당겨지게 가운데 단에서 이미 끝까지)
--     Y: +1 = 몸이 뒤로 25도 (W·Shift: 캐노피를 앞으로 숙인 모양), 0 = 중립, -1 = 플레어(S): 몸 앞으로 25도,
--        양손 허리 앞까지, 허벅지 45도·무릎 35도 (선회 쪽 손은 조금 더 아래)
--     클립 이름: 줄 Back / (없음) / Flare + 칸 L, L1, (가운데), R1, R. 예) HaloDropCP_BackL1, HaloDropCP_R, HaloDropCP_Flare
--     낙하산 블렌드는 일정 속도 추종 대신 감쇠가 덜 된 스프링으로 따라간다 (HaloDrop.POSE.SPRING_*):
--     키를 누르면 늦게 쓸려 나가고 목표를 넘었다 돌아오며, W 를 짧게 반복하면 앞뒤로 흔들린다.
--   클립은 anims_X/Bob 에 있다 (42/media/anims_X 의 사용자 폴더는 common/media/anims_X 가 없으면 로드되지 않는다).
--
-- 블렌드 값: 점퍼(HaloDropJumper)는 매 틱 "목표"만 정하고(setTarget), 실제 값은 여기서 따라간다
--   (자유낙하 = HaloDrop.POSE RATE_X/Y 일정 속도, 낙하산 = 스프링).
-- 네트워크: 사용자 정의 애니메이션 변수는 네트워크로 안 간다(PlayerVariables 는 정해진 변수만 보낸다).
--   자세 이름은 바뀔 때마다 "Pose", 블렌드는 목표값을 NET_STEP 단위로 끊어 바뀔 때만 "PoseTarget" 으로 서버에 보낸다.
--   목표는 입력이 바뀔 때만 바뀌므로 보내는 양이 적다. 받는 클라는 같은 속도로 따라가서 같은 모양이 된다.

HaloDropPose = HaloDropPose or {}

local VAR = "HaloDropPose"
local BLEND_VARS = {
    freefall = { "HaloDropFFX", "HaloDropFFY" },
    canopy   = { "HaloDropCPX", "HaloDropCPY" },
}

local _mine = nil      -- 내 현재 자세
local _remote = {}     -- [onlineID] = pose (다른 플레이어)
local _blend = {}      -- [key] = { x, y, tx, ty } key = "me" 또는 onlineID
local _net = { sentX = nil, sentY = nil, lastMs = 0, pendX = nil, pendY = nil }
local _nextSweep = 0
local _lastTick = nil
local _nextBlendLog = 0
local BLEND_LOG_MS = 250   -- 진단: 낙하산 중 블렌드 값 로그 간격 (자바 VpPose 의 canopy bones 로그와 같은 간격)

local function log(msg) HaloDrop.log(msg) end

local function apply(p, pose)
    if pose then
        p:setVariable(VAR, pose)
    else
        p:clearVariable(VAR)
    end
end

local function applyBlend(p, pose, b)
    local names = pose and BLEND_VARS[pose]
    if not names or not b then return end
    p:setVariable(names[1], b.x)
    p:setVariable(names[2], b.y)
end

-- 자유낙하 블렌드는 다이브(0, 2) 쪽에서 좌우 폭이 줄어든다(삼각형 끝). 범위 밖이면 가장자리로 투영되긴 하지만 미리 자른다
local function clampTarget(pose, x, y)
    if x < -1 then x = -1 elseif x > 1 then x = 1 end
    if pose == "canopy" then
        if y < -1 then y = -1 elseif y > 1 then y = 1 end
        return x, y
    end
    if y < -1 then y = -1 elseif y > 2 then y = 2 end
    if y > 1 then
        local w = 2 - y
        if x > w then x = w elseif x < -w then x = -w end
    end
    return x, y
end

local function quant(v)
    local s = HaloDrop.POSE.NET_STEP
    return math.floor(v / s + 0.5) * s
end

local function sendTarget(p, x, y, now)
    _net.sentX, _net.sentY, _net.lastMs = x, y, now
    _net.pendX, _net.pendY = nil, nil
    sendClientCommand(p, HaloDrop.MODULE, "PoseTarget", { x = x, y = y })
    log(string.format("pose target sent: %s x=%.1f y=%.1f", tostring(_mine), x, y))
end

-- 내 캐릭터 자세를 바꾼다. 같은 값이면 아무것도 안 한다. 자세가 바뀌면 블렌드는 가운데(0, 0)에서 다시 시작한다.
function HaloDropPose.set(p, pose)
    if pose == _mine then return end
    local prev = _mine
    _mine = pose
    local ok, err = pcall(apply, p, pose)
    log(string.format("pose: %s -> %s ok=%s%s", tostring(prev), tostring(pose), tostring(ok), ok and "" or (" err=" .. tostring(err))))
    sendClientCommand(p, HaloDrop.MODULE, "Pose", { pose = pose or "" })
    if pose and BLEND_VARS[pose] then
        _blend.me = { x = 0, y = 0, tx = 0, ty = 0, vx = 0, vy = 0 }
        pcall(applyBlend, p, pose, _blend.me)
    else
        _blend.me = nil
    end
    _net.sentX, _net.sentY, _net.pendX, _net.pendY = 0, 0, nil, nil
end

function HaloDropPose.get()
    return _mine
end

-- 내 자세 블렌드 현재값·목표값 (진단 로그용). 자세가 없으면 nil
function HaloDropPose.blend()
    local b = _blend.me
    if not b then return nil end
    return b.x, b.y, b.tx, b.ty
end

-- 점퍼가 매 틱 부른다: 이번 입력의 블렌드 목표 (자세 좌표계는 머리말 참고)
function HaloDropPose.setTarget(p, x, y)
    local b = _blend.me
    if not b or not _mine then return end
    x, y = clampTarget(_mine, x, y)
    b.tx, b.ty = x, y
    -- 네트워크: NET_STEP 단위로 끊고, 바뀌었을 때만, NET_MIN_MS 간격 이상으로
    local qx, qy = quant(x), quant(y)
    if qx ~= _net.sentX or qy ~= _net.sentY then
        _net.pendX, _net.pendY = qx, qy
    else
        _net.pendX, _net.pendY = nil, nil
    end
    local now = getTimestampMs()
    if _net.pendX and now - _net.lastMs >= HaloDrop.POSE.NET_MIN_MS then
        sendTarget(p, _net.pendX, _net.pendY, now)
    end
end

-- 다른 플레이어 자세·블렌드 목표 (서버 중계)
local function onServerCommand(module, command, args)
    if module ~= HaloDrop.MODULE or not args then return end
    if command ~= "Pose" and command ~= "PoseTarget" then return end
    local me = getPlayer()
    if not me or args.oid == nil or args.oid == me:getOnlineID() then return end
    if command == "Pose" then
        local pose = args.pose
        if pose == "" then pose = nil end
        _remote[args.oid] = pose
        if pose and BLEND_VARS[pose] then
            _blend[args.oid] = { x = 0, y = 0, tx = 0, ty = 0, vx = 0, vy = 0 }
        else
            _blend[args.oid] = nil
        end
        local rp = getPlayerByOnlineID(args.oid)
        local ok = false
        if rp then
            ok = pcall(apply, rp, pose)
            pcall(applyBlend, rp, pose, _blend[args.oid])
        end
        log(string.format("pose remote: oid=%s pose=%s found=%s ok=%s", tostring(args.oid), tostring(pose), tostring(rp ~= nil), tostring(ok)))
        return
    end
    local b = _blend[args.oid]
    local x, y = tonumber(args.x), tonumber(args.y)
    if not b or not x or not y then return end
    b.tx, b.ty = clampTarget(_remote[args.oid], x, y)
end
Events.OnServerCommand.Add(onServerCommand)

-- 다른 플레이어 점퍼: 애니메이션 각도를 그 플레이어 몸 방향에 바로 맞춘다. 내 캐릭터는 점퍼(HaloDropJumper face)가 한다.
-- 바닐라 상체 비틀기가 엎드린·거꾸로 선 자세에서 허리를 옆으로 꺾기 때문 (HaloDropJumper face 주석 참고)
local _remoteSnapLogged = {}
local function snapRemote(oid, pl)
    local ok, err = pcall(function()
        pl:setTargetAndCurrentDirection(pl:getForwardDirectionX(), pl:getForwardDirectionY())
    end)
    if not _remoteSnapLogged[oid] then
        _remoteSnapLogged[oid] = true
        log(string.format("pose remote: body angle snap oid=%s ok=%s%s", tostring(oid), tostring(ok), ok and "" or (" err=" .. tostring(err))))
    end
end

local function approach(v, target, rate, dt)
    local k = rate * dt
    if k > 1 then k = 1 end
    return v + (target - v) * k
end

-- 낙하산 그네: 감쇠가 덜 된 스프링 (주기 SPRING_PERIOD 초, 감쇠비 SPRING_DAMP).
-- 저 FPS 에서 발산하지 않게 1/60초 이하로 나눠 적분하고, 블렌드 범위(+-1) 밖으로는 나가지 않게 막는다.
local SPRING_SUBSTEP = 1 / 60
local function spring(b, dt)
    local P = HaloDrop.POSE
    local w = 2 * math.pi / P.SPRING_PERIOD
    local k, c = w * w, 2 * P.SPRING_DAMP * w
    b.vx, b.vy = b.vx or 0, b.vy or 0
    local left = dt
    while left > 0 do
        local h = left < SPRING_SUBSTEP and left or SPRING_SUBSTEP
        left = left - h
        b.vx = b.vx + (k * (b.tx - b.x) - c * b.vx) * h
        b.vy = b.vy + (k * (b.ty - b.y) - c * b.vy) * h
        b.x = b.x + b.vx * h
        b.y = b.y + b.vy * h
        if b.x > 1 then b.x, b.vx = 1, 0 elseif b.x < -1 then b.x, b.vx = -1, 0 end
        if b.y > 1 then b.y, b.vy = 1, 0 elseif b.y < -1 then b.y, b.vy = -1, 0 end
    end
end

-- 매 틱: 블렌드 값을 목표로 따라가게 하고 변수에 넣는다. 1초마다 원격 플레이어 자세 변수도 다시 세운다
-- (플레이어 객체가 나중에 만들어지거나 다시 만들어지는 경우 대비)
local function onTick()
    local now = getTimestampMs()
    local dt = (now - (_lastTick or now)) / 1000
    if dt < 0 then dt = 0 elseif dt > 0.25 then dt = 0.25 end
    _lastTick = now
    local P = HaloDrop.POSE
    for key, b in pairs(_blend) do
        local pl, pose
        if key == "me" then
            pl, pose = getPlayer(), _mine
        else
            pl, pose = getPlayerByOnlineID(key), _remote[key]
        end
        if pose == "canopy" then
            spring(b, dt)
        else
            b.x = approach(b.x, b.tx, P.RATE_X, dt)
            b.y = approach(b.y, b.ty, P.RATE_Y, dt)
        end
        if pl then
            pcall(applyBlend, pl, pose, b)
            if key ~= "me" then snapRemote(key, pl) end
        end
        -- 진단 (11-23 캐노피 그네 시험): 내 낙하산 블렌드 현재값·목표값. 같은 시각의 자바 "canopy bones" 로그와 맞춰 본다
        if key == "me" and pose == "canopy" and now >= _nextBlendLog then
            _nextBlendLog = now + BLEND_LOG_MS
            log(string.format("canopy blend x=%.2f y=%.2f target=(%.2f,%.2f) v=(%.2f,%.2f)", b.x, b.y, b.tx, b.ty, b.vx or 0, b.vy or 0))
        end
    end
    -- 보내지 못하고 남은 마지막 목표 (간격 제한에 걸린 것)
    if _net.pendX and _mine and now - _net.lastMs >= P.NET_MIN_MS then
        local p = getPlayer()
        if p then sendTarget(p, _net.pendX, _net.pendY, now) end
    end
    if now < _nextSweep then return end
    _nextSweep = now + 1000
    for oid, pose in pairs(_remote) do
        local rp = getPlayerByOnlineID(oid)
        if rp then pcall(apply, rp, pose) end
    end
end
Events.OnTick.Add(onTick)
