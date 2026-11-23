-- Halo Drop: 자유낙하 광각(어안) 연출 (11-23)
--
-- 3000m 자유낙하가 날아가는 모습만 보여 밋밋했다. 낙하산(캐노피)을 펴기 전까지 Viewpoint 1·3인칭 화면을
-- 광각 렌즈처럼 바꾼다: 시야각을 넓히고(가까운 건 크게, 먼 건 작게), 화면을 둥글게 휘고(지평선이 휜다),
-- 3인칭 카메라를 캐릭터 쪽으로 당긴다. 아이소 화면은 3D 카메라가 없어서 아무 일도 없다.
--
-- Viewpoint 설정창(Delete 키)의 값은 읽지도 쓰지도 않는다. 여기서는 세기(k)만 정해 HaloDrop.LensState 에
-- 올리고, HaloDropClient.jar 의 Lens 가 Viewpoint 가 값을 꺼내 쓰는 순간에만 바꿔 준다(자바 Lens.java 머리말).
-- 그래서 강하가 끝나면(또는 이 파일이 멈추면 1초 안에) 플레이어가 설정해 둔 화면으로 그대로 돌아온다.
--
-- 세기 k (0 ~ 1): 하강 속도 vz 를 따라간다. 뛰어내린 직후 0 -> 무입력 속도에서 K_NEUTRAL -> Shift 다이브 속도에서 1.
--   낙하산을 펴면 RATE_CHUTE 로 0 까지 빠르게 줄이고 끝낸다.
-- HaloDrop.LensState = { fov, cap, panini, pull, at }: 매 틱 새 표로 통째로 바꾼다(자바가 다른 스레드에서 읽는다).
--   fov = 플레이어 시야각에서 cap(도)까지 가는 비율, panini = 화면을 휘는 세기, pull = 카메라를 당기는 비율,
--   at = getTimestampMs. 값의 뜻과 실제 화면 효과는 첫 런에서 [HaloDrop][Lens] 로그로 확인한다.

if isServer() then return end

require "HaloDropConfig"

HaloDropLens = HaloDropLens or {}

local S = { k = nil, bucket = nil }

local function log(msg) HaloDrop.log(msg) end

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
    S.bucket = nil
    log(string.format("lens: start (cap=%d fov=%.2f panini=%.2f pull=%.2f, viewpoint fov now=%s)", L.FOV_CAP, L.FOV, L.PANINI, L.PULL, viewFov()))
end

-- 매 틱 (HaloDropJumper onPlayerUpdate, FREEFALL / CANOPY). vz = 하강 속도(m/초, 아래가 +)
function HaloDropLens.tick(state, vz, dt, now)
    if S.k == nil then return end
    local L = HaloDrop.LENS
    local freefall = state == "FREEFALL"
    local target = 0
    if freefall then
        local s = vz / HaloDrop.opt("FreefallSpeed")
        if s < 0 then s = 0 elseif s > 1 then s = 1 end
        local n = HaloDrop.FF.neutral.v
        if s <= n then
            target = L.K_NEUTRAL * s / n
        else
            target = L.K_NEUTRAL + (1 - L.K_NEUTRAL) * (s - n) / (1 - n)
        end
    end
    if dt < 0 then dt = 0 elseif dt > 0.1 then dt = 0.1 end
    local rate = L.RATE_CHUTE
    if freefall then
        if target > S.k then rate = L.RATE_IN else rate = L.RATE_OUT end
    end
    local d = target - S.k
    local step = rate * dt
    if d > step then d = step elseif d < -step then d = -step end
    S.k = S.k + d
    if not freefall and S.k <= 0.001 then
        HaloDropLens.stop("canopy open, faded out")
        return
    end
    HaloDrop.LensState = { fov = L.FOV * S.k, cap = L.FOV_CAP, panini = L.PANINI * S.k, pull = L.PULL * S.k, at = now }
    local bucket = math.floor(S.k * 4 + 0.5)
    if bucket ~= S.bucket then
        S.bucket = bucket
        log(string.format("lens: k=%.2f state=%s vz=%.1f -> fov share %.2f panini %.2f pull %.2f (viewpoint fov now=%s)",
            S.k, tostring(state), vz, L.FOV * S.k, L.PANINI * S.k, L.PULL * S.k, viewFov()))
    end
end

function HaloDropLens.stop(why)
    if S.k == nil then return end
    S.k = nil
    S.bucket = nil
    HaloDrop.LensState = nil
    log("lens: stop (" .. tostring(why) .. ")")
end
