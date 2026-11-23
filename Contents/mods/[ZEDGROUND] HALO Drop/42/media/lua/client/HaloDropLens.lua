-- Halo Drop: 자유낙하 광각 연출 + 스피드라인 오버레이 (11-23)
--
-- 3000m 자유낙하가 날아가는 모습만 보여 밋밋했다. 낙하산(캐노피)을 펴기 전까지 Viewpoint 1·3인칭 화면을
-- 광각 렌즈처럼 바꾼다: 시야각을 넓히고(가까운 건 크게, 먼 건 작게), 3인칭 카메라를 캐릭터 쪽으로 당긴다.
-- 아이소 화면은 3D 카메라가 없어서 렌즈는 아무 일도 없다 (스피드라인은 UI 라 그려진다).
--
-- Viewpoint 설정창(Delete 키)의 값은 읽지도 쓰지도 않는다. 여기서는 세기만 정해 HaloDrop.LensState 에
-- 올리고, HaloDropClient.jar 의 Lens 가 Viewpoint 가 값을 꺼내 쓰는 순간에만 바꿔 준다(자바 Lens.java 머리말).
-- 그래서 강하가 끝나면(또는 이 파일이 멈추면 1초 안에) 플레이어가 설정해 둔 화면으로 그대로 돌아온다.
--
-- 세기 k (0 ~ 1): 하강 속도 vz 를 따라간다. 뛰어내린 직후 0 -> 무입력 속도에서 K_NEUTRAL -> Shift 다이브 속도에서 1.
--   낙하산을 펴면 RATE_CHUTE 로 0 까지 빠르게 줄이고 끝낸다.
-- 볼록 정도 c (0 ~ 1, 11-23 2차): 높을 때는 화면을 둥글게 휘어(파니니, 볼록·어안 쪽) 높이감을 주고, 지면에 가까울수록
--   그리고 다이브할수록 파니니를 빼서 넓은 시야각의 가장자리 늘어남(오목 쪽)으로 넘어가 돌진감을 준다.
--   c = 발밑 지면 기준 고도로 PANINI_ALT_LOW(0) ~ PANINI_ALT_HIGH(1) 보간 x (1 - DIVE_CUT x 다이브 비율). RATE_CONVEX 로 따라간다.
--   파니니 = PANINI x k x c. 시야각·당김은 k 만 따른다.
-- HaloDrop.LensState = { fov, cap, panini, pull, at }: 매 틱 새 표로 통째로 바꾼다(자바가 다른 스레드에서 읽는다).
-- 스피드라인(HaloDropSpeedLines): 투명도 = ALPHA_MAX x k x (HIGH_SHARE + (1 - HIGH_SHARE) x (1 - c)). 오목 쪽일수록 진하다.

if isServer() then return end

require "HaloDropConfig"
require "HaloDropSpeedLines"

HaloDropLens = HaloDropLens or {}

local S = { k = nil, c = 1, bucket = nil, cBucket = nil }

local function log(msg) HaloDrop.log(msg) end

local function clamp01(v)
    if v < 0 then return 0 elseif v > 1 then return 1 end
    return v
end

local function approach(v, target, rate, dt)
    local d = target - v
    local step = rate * dt
    if d > step then d = step elseif d < -step then d = -step end
    return v + d
end

-- Viewpoint 가 지금 쓰는 시야각(도). 로그 확인용이다 (jar 가 넓힌 값이 여기 그대로 보이면 적용된 것)
local function viewFov()
    local ok, v = pcall(function()
        if Viewpoint and Viewpoint.Addons and Viewpoint.Addons.viewState then
            local st = Viewpoint.Addons.viewState()
            return st and st.fov or nil
        end
        return nil
    end)
    if ok and v then return string.format("%.1f", v) end
    return "none"
end

function HaloDropLens.start()
    local L = HaloDrop.LENS
    if not L or not L.ON then return end
    S.k = 0
    S.c = 1
    S.bucket = nil
    S.cBucket = nil
    log(string.format("lens: start (cap=%d fov=%.2f panini=%.2f pull=%.2f, convex above %dm agl, flat below %dm, dive cut %.2f, viewpoint fov now=%s)",
        L.FOV_CAP, L.FOV, L.PANINI, L.PULL, L.PANINI_ALT_HIGH, L.PANINI_ALT_LOW, L.DIVE_CUT, viewFov()))
    HaloDropSpeedLines.start()
end

-- 매 틱 (HaloDropJumper onPlayerUpdate, FREEFALL / CANOPY). vz = 하강 속도(m/초, 아래가 +), agl = 발밑 지면 기준 가상 고도(m, 없으면 nil)
function HaloDropLens.tick(state, vz, dt, now, agl)
    if S.k == nil then return end
    local L = HaloDrop.LENS
    local freefall = state == "FREEFALL"
    local target = 0
    local diveK = 0
    if freefall then
        local s = clamp01(vz / HaloDrop.opt("FreefallSpeed"))
        local n = HaloDrop.FF.neutral.v
        if s <= n then
            target = L.K_NEUTRAL * s / n
        else
            diveK = (s - n) / (1 - n)
            target = L.K_NEUTRAL + (1 - L.K_NEUTRAL) * diveK
        end
    end
    if dt < 0 then dt = 0 elseif dt > 0.1 then dt = 0.1 end
    local rate = L.RATE_CHUTE
    if freefall then
        if target > S.k then rate = L.RATE_IN else rate = L.RATE_OUT end
    end
    S.k = approach(S.k, target, rate, dt)
    if not freefall and S.k <= 0.001 then
        HaloDropLens.stop("canopy open, faded out")
        return
    end
    local altC = 1
    if agl then altC = clamp01((agl - L.PANINI_ALT_LOW) / (L.PANINI_ALT_HIGH - L.PANINI_ALT_LOW)) end
    local cTarget = altC * (1 - L.DIVE_CUT * diveK)
    if freefall then S.c = approach(S.c, cTarget, L.RATE_CONVEX, dt) end
    local pan = L.PANINI * S.k * S.c
    HaloDrop.LensState = { fov = L.FOV * S.k, cap = L.FOV_CAP, panini = pan, pull = L.PULL * S.k, at = now }
    local SLc = HaloDrop.SPEEDLINES
    local lineA = 0
    if SLc and SLc.ON then
        lineA = SLc.ALPHA_MAX * S.k * (SLc.HIGH_SHARE + (1 - SLc.HIGH_SHARE) * (1 - S.c))
    end
    HaloDropSpeedLines.set(lineA)
    local bucket = math.floor(S.k * 4 + 0.5)
    local cBucket = math.floor(S.c * 4 + 0.5)
    if bucket ~= S.bucket or cBucket ~= S.cBucket then
        S.bucket = bucket
        S.cBucket = cBucket
        log(string.format("lens: k=%.2f c=%.2f state=%s vz=%.1f agl=%s -> fov share %.2f panini %.2f pull %.2f lines %.2f (viewpoint fov now=%s)",
            S.k, S.c, tostring(state), vz, agl and string.format("%.0f", agl) or "nil", L.FOV * S.k, pan, L.PULL * S.k, lineA, viewFov()))
    end
end

function HaloDropLens.stop(why)
    if S.k == nil then return end
    S.k = nil
    S.bucket = nil
    S.cBucket = nil
    HaloDrop.LensState = nil
    HaloDropSpeedLines.stop(why)
    log("lens: stop (" .. tostring(why) .. ")")
end
