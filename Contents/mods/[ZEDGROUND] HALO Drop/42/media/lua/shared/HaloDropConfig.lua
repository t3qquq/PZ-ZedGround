-- Halo Drop 공용 상수·헬퍼 (서버·클라 공용)
--
-- 고도 체계
--   가상 고도(m): 탑승·자유낙하의 "진짜" 높이. 화면에는 고도계로만 보인다.
--   실제 z(층): 캐릭터가 실제로 있는 층. 뛰어내릴 때 CarryZ, 이후 가상 고도에 맞춰 DESCENT_Z.LOW_Z(3) 까지
--     내려오고(HaloDropJumper visualZ), 그 아래는 엔진 낙하. 카메라는 캐릭터 z 를 따라가고
--     (PlayerCamera / calculateCameraZ) 청크는 x, y 기준으로만 로드되므로(IsoChunkMap.ProcessChunkPos)
--     실제 z 를 높이 올리면 화면 중심 지면이 로드 범위 밖으로 밀린다. 1층당 x, y 각 3칸.
--     로드 창 폭은 해상도로 정해진다(IsoChunkMap.CalcChunkWidth: 1080p 13, 1440p 이상 19 청크 = +-72칸).

HaloDrop = HaloDrop or {}

HaloDrop.MODULE       = "HaloDrop"           -- 커맨드 모듈명 (서버·클라 공용, 다른 모드와 겹치지 않게)
HaloDrop.PLANE_SCRIPT = "Base.HaloDropC17"
HaloDrop.CHUTE_ITEM   = "HaloDrop.HaloDropParachute"

HaloDrop.M_PER_FLOOR  = 2.45                 -- 가상 고도(m) <-> 층. 엔진 카메라 환산 2.44949
HaloDrop.PHYS_PER_FLOOR = 2.46               -- 차량 물리 y <-> 층. 퐁듀 수송기(PLANE_FLY_ALT)와 같은 값
HaloDrop.PREP_MS      = 5000                 -- 라운드 시작 방송 후 수송기가 A 를 떠나기까지
HaloDrop.JUMP_PLANE_GRACE_MS = 8000          -- 점프: 발밑 땅이 올라온 뒤 수송기 없이 기다리는 최대 시간
-- 런 8(11-14): 수송기가 플레이어 머리 위를 지나가면 같은 층(PlaneZ = CarryZ = 31)이라 3인칭 카메라를 뚫고 화면을 덮었다.
-- -> 플레이어를 수송기 "뒤"에서 떨군다: 수송기는 점프 지점 앞 LEAD 칸에서 나타나 앞으로 날아가고, 플레이어는 꼬리 뒤에서 떨어진다.
HaloDrop.JUMP_PLANE_LEAD     = 34            -- 점프 수송기 시작 위치: 점프 지점 앞쪽(타일, 기체 중심). C-17 길이 약 53칸이라 꼬리가 플레이어 앞 약 8칸
HaloDrop.JUMP_PLANE_AHEAD    = 64            -- 점프 수송기 끝 위치: 점프 지점 앞쪽(타일). 지나면 제거. 로드 창 밖 칸은 그 틱 이동을 건너뛴다(skipped)
HaloDrop.JUMP_PLANE_SPEED    = 12            -- 점프 수송기 속도(타일/초). LEAD 34 -> AHEAD 64 = 2.5초 동안 멀어지는 모습이 보인다
HaloDrop.JUMP_PLANE_DROP_MS  = 600           -- 수송기가 보이기 시작한 뒤 뛰어내리기까지(ms). 구름이 걷히고 꼬리 뒤에 있는 장면을 잠깐 보여준다
HaloDrop.JUMP_PLANE_MAX_MS   = 20000         -- 점프 수송기 최대 수명(서버). 클라 PlaneDone 이 안 와도 지운다
-- 서버 셀(64x64칸) 미리 올리기 (server/HaloDropPreload.lua). 전용 서버를 -javaagent:HaloDropAgent.jar 로 띄웠을 때만 동작한다.
-- 경로 미리 생성 (C안, 11-17): 처음 가는 지역은 서버가 청크를 새로 만들어야 해서 강하 중 수평 이동이 막힌다
-- (같은 곳 두 번째 비행은 덜 막힘). 라운드 시작을 누르면 경로를 먼저 정하고, 수송기가 뜨기 전에 경로 양옆
-- 셀(64x64칸)을 하나씩 올렸다 내려 저장해 둔다. 다 되면(또는 MAX_WAIT_MS, 또는 어드민 "준비 건너뛰고 출발") 출발.
-- 서버 셀 로딩은 LoadChunk 스레드 하나가 FIFO 로 처리한다(ServerChunkLoader). MAX_PENDING 을 키워도
-- 빨라지지 않고 플레이어 쪽 로딩만 뒤로 밀린다. 2 = 하나 끝나면 바로 다음이 들어가게 하는 최소값.
HaloDrop.ROUTE_PREGEN = {
    ENABLED     = true,
    HALF_WIDTH  = 96,       -- 경로 중심선에서 양옆으로 미리 만들 거리(타일). 96 = 셀 1.5개 -> 폭 약 3~4셀
    MAX_PENDING = 2,
    MAX_WAIT_MS = 600000,   -- 준비 최대 시간(10분). 넘으면 남은 셀은 두고 출발
    REPORT_MS   = 5000,     -- 진행 로그·방송 주기
}

-- 비행 경로 알고리즘 (샌드박스 HaloDrop.RouteMode, 11-22). 번호 -> 이름. 실제 선정 함수는 server/HaloDropServer.lua ROUTE_PICKERS.
-- 새 방식을 넣을 때: 여기 이름 추가 -> ROUTE_PICKERS 에 같은 번호로 함수 등록 -> sandbox-options.txt numValues +1
--                     -> Translate EN/KO Sandbox.json 에 Sandbox_HaloDrop_RouteMode_optionN 추가
HaloDrop.ROUTE_MODES = {
    [1] = "VillageToVillage",
}

-- 빌리지 투 빌리지 (RouteMode 1): 마을 기준 A'->A->B->B' (11-18, server/HaloDropServer.lua pickVillageFlight).
--   A = TOWNS 중 무작위 한 곳의 중점에서 반경 TOWN_RADIUS 안 무작위 점.
--   B = A 마을과 중점 거리가 MAX_PAIR 두 마을 사이 거리 이하인 다른 마을에서 같은 방식으로 뽑은 점.
--   A' = A 에서 B 반대쪽으로 |AB| x EXTEND, B' = B 에서 진행 방향으로 |AB| x EXTEND (EXTEND 0.25 = 전체 1.5배, 0.5 = 2배).
--   A'·B' 는 비행 박스(WorldX1~Y2) 밖이나 빈 북서쪽(AvoidEmptyNW)에 닿으면 그 앞에서 자른다.
-- 루이빌·밸리스테이션은 후보에서 뺀다 (어빙턴~루이빌처럼 지나치게 긴 경로가 걸리면 준비 시간이 오히려 늘어서).
-- 중점 = pzmap.org 42.20 마을 이름표 위치. 비행 박스 밖 마을은 후보에서 빠지고, 2곳 미만이면 이전 무작위 직선 방식.
HaloDrop.ROUTE = {
    TOWN_RADIUS = 1280,                     -- 20 셀(64칸) = 1280칸
    EXTEND      = 0.25,
    MAX_PAIR    = { "Muldraugh", "Ekron" }, -- 약 10240칸
    TOWNS = {
        { name = "Brandenburg", x = 2233,  y = 6208 },
        { name = "Ekron",       x = 677,   y = 9933 },
        { name = "Coalfield",   x = 3587,  y = 8326 },
        { name = "Riverside",   x = 6575,  y = 5413 },
        { name = "Echo Creek",  x = 3650,  y = 11058 },
        { name = "Fallas Lake", x = 7408,  y = 8477 },
        { name = "Irvington",   x = 2630,  y = 14385 },
        { name = "Rosewood",    x = 8239,  y = 11708 },
        { name = "Muldraugh",   x = 10916, y = 10082 },
        { name = "March Ridge", x = 10253, y = 12852 },
        { name = "West Point",  x = 11828, y = 6967 },
        -- 제외: Louisville (13068, 2370), Valley Station (13647, 5214)
    },
}

HaloDrop.PRELOAD = {
    ENABLED      = true,
    AGENT_VERSION = 1.0,  -- server-agent/HaloDropAgent.jar 의 Agent.VERSION. jar 를 바꾸면 같이 올린다 (다르면 재설치 경고)
    -- 적용 구간: 수송기에서 뛰어내린 순간부터 착지 전까지만. 탑승 중(비행)·착지 후에는 미리 올리지 않는다.
    PLAYER_CELLS = 2,     -- 공중 플레이어 주변으로 올릴 셀 수 (2 = 5x5셀, 클라 로드 범위보다 한 셀 넓게)
    MAX_PENDING  = 2,     -- 동시에 로드를 요청할 셀 수. 크면 빨리 올라오지만 서버 프레임이 더 오래 멈출 수 있다
    REPORT_MS    = 5000,  -- 로그 주기
}
-- 뛰어내린 순간 이어받는 수평 속도(타일/초) 상한. 런 5: fl.speed(80) 을 그대로 받아 첫 0.6초에 30칸을 날아가
-- 로드 창 밖으로 나갔다 -> 3초 제자리 스냅백 -> far move -> 10초 current==null(화면 검정·갱신 정지).
-- 눈에 보이는 점프 수송기는 JUMP_PLANE_SPEED 로 지나가므로 그 속도를 이어받는 게 맞다.
HaloDrop.JUMP_VH_MAX         = HaloDrop.JUMP_PLANE_SPEED
-- 그 중 실제로 이어받는 비율. 배그 실측: 잔류 수평 50~70km/h 가 5~8초에 사라져 체감은 제자리 (claude/PUBG_강하_조작명세서.md 1절)
HaloDrop.JUMP_VH_FRAC        = 0.15
-- 자동 개방: AutoOpenAltitude 에 닿은 뒤 이 시간(ms)이 지나야 낙하산 상태가 된다. 그동안은 자유낙하 속도로 계속 떨어진다.
-- 배그 실측 두 점(무입력 150km/h 는 약 290m, 234km/h 다이브는 약 250m 에서 펴짐)을 동시에 맞추는 값:
--   290 = A - 41.7 x d, 250 = A - 65 x d  ->  d = 1.7초, A = 360m. 배그 수동 전개도 키 입력 약 2초 뒤에 펴진다(같은 전개 시간).
-- 수동 개방(상호작용 키)은 지연·잠금 없이 바로 펴진다 (전개 연출 없음, 배그의 고도 기준 수동 전개 잠금도 넣지 않는다 - 11-16 결정).
HaloDrop.CHUTE_OPEN_MS       = 1700
-- 전개 직후 급감속(ChuteDecel)을 끝내는 기준: 하강 속도가 CanopySpeed 의 이 배수 아래로 내려오면 CanopyAccel 로 넘어간다
HaloDrop.CHUTE_DECEL_END     = 1.15
-- 점프 전 주변 청크 대기 (HaloDropJumper areaCheck). 런 10(11-15): 발밑 칸만 올라오면 바로 떨궈서, 점프 지점 도착 직후
-- 아직 덜 받은 주변 청크에 걸려 수평 이동이 8번(합계 3.4초) 멈췄다 -- 로드 창 한가운데서 바로 옆 칸이 없었다.
-- 구름으로 가린 채 주변 반경 CHUNKS 청크가 올라올 때까지 수송기 출발(HaloDropPilot)을 미룬다.
HaloDrop.JUMP_READY = {
    CHUNKS     = 5,      -- 플레이어 청크 기준 반경(청크). 5 = 11x11 청크 = +-40칸. 로드 창 최소 폭(13 청크) 안쪽
    READY_FRAC = 0.95,   -- 이 비율 이상 올라오면 준비 완료 (맵 가장자리처럼 영영 안 오는 청크 대비)
    MAX_MS     = 10000,  -- 점프 시작(E) 뒤 이 시간이 지나면 덜 올라왔어도 진행
    CHECK_MS   = 250,    -- 검사 주기
}

-- 착지 감속. 퐁듀 zombierain.lua 의 LAND_SPEED / FREE_FALL_BRAKE_H 와 같은 값.
-- 착지 피해 기준은 충돌 속도 약 2.24 층/초(FallingConstants). updateFalling 이 착지 틱에
-- 중력분(5.0 x dt)을 한 번 더 더해도 10fps 에서 1.2 + 0.5 = 1.7 로 여유가 있다.
HaloDrop.LAND_SPEED   = 1.2
HaloDrop.BRAKE_H      = 1.0
-- 캐노피로 LOW_Z 아래(엔진 낙하)에 들어갈 때 화면 하강 속도를 붙잡기 구간 속도에서 LAND_SPEED 까지 올리는 시간
-- (HaloDropJumper release). 3층을 약 3초에 내려온다. 이전엔 엔진 속도(vz/2.45, 3~6층/초)로 1초 안에 떨어졌다.
HaloDrop.RELEASE_RAMP_MS = 1500

-- ── 강하 모델 (PUBG 실측 11-16, claude/PUBG_강하_조작명세서.md 3절 / 8절) ──────────────────
-- 환산 규칙(명세서 7절): 수직은 1:1(km/h / 3.6), 수평은 배그 값 x k (k = FreefallSteer / 34), 회전·시간 상수는 1:1.
-- 자유낙하: v = FreefallSpeed(65 m/s = Shift 다이브 234km/h) 대비 비율, h = FreefallSteer 대비 비율.
HaloDrop.FF = {
    neutral   = { v = 0.64, h = 0.00 },   -- 무입력: 150 km/h, 수평 0
    glide     = { v = 0.77, h = 1.00 },   -- W + 카메라 수평: 180 / 122 km/h
    glideDown = { v = 1.00, h = 0.33 },   -- W + 카메라 아래: 232 / 약 40 km/h (카메라 피치로 glide 와 보간)
    dive      = { v = 1.00, h = 0.00 },   -- 달리기 키(Shift) 단독: 234 km/h, 카메라 각도와 무관
    brake     = { v = 0.59, h = 0.00 },   -- S: 137 km/h
    strafe    = { v = 0.59, h = 0.65 },   -- A / D 단독: 138 km/h, 옆으로 약 80 km/h (h 는 heading 의 좌우 수직 방향)
}
-- 낙하산: v = CanopySpeed(6.9 m/s = 무입력 25km/h) 대비 비율, h = CanopySteer(W 72km/h x k) 대비 비율.
-- 낙하산 높이별 하강 가속 (HaloDropJumper). 발밑 바닥 기준 높이(m). LOW 아래 x1(배그 25km/h), HIGH 이상 x MAX, 사이는 직선.
-- 뛰는 고도를 배그의 2배(3000m)로 올려서 일찍 펴면 낙하산 구간이 5분 가까이 됐다 (11-23). x4 / 300~1500m 면 2400m 전개 시 약 2분 20초
-- CAP: 낙하산 하강 상한(m/초). 높이 배수 x W·Shift 가 자유낙하(약 42~50)를 넘지 않게 = 108km/h
HaloDrop.CANOPY_ALT_SPEED = { LOW = 300, HIGH = 1500, MAX = 4.0, CAP = 30.0 }

HaloDrop.CP = {
    neutral = { v = 1.00, h = 0.00 },   -- 무입력: 25 km/h, 수평 0
    glide   = { v = 1.80, h = 1.00 },   -- W: 45 / 72 km/h. 배그 실측은 하강 25 그대로였지만 W 로 빨리 내려가고 싶다는 t3 요청 (11-23)
    dive    = { v = 3.20, h = 0.80 },   -- 달리기 키(Shift): 80 / 57 km/h. W 유무와 무관 (배그 54, 11-23 t3 요청으로 올림)
    brake   = { v = 0.32, h = 0.00 },   -- S: 8 km/h, 거의 정지
    slow    = { v = 0.44, h = 0.87 },   -- Ctrl: 11 / 62 km/h 장거리 활공. W 유무와 무관
    turn    = { v = 2.20, h = 0.75 },   -- W + A / D: 55 / 약 55 km/h, 선회 CanopyTurnRateFwd (W 보다 빠르게)
    spin    = { v = 1.00, h = 0.40 },   -- A / D 단독: 하강은 그대로 25~27 km/h, 선회 CanopyTurnRate
}

-- ── 조향 카메라 (HaloDropJumper readCamera). 자유낙하는 몸이 Viewpoint 카메라 방향을 따라간다 (명세서 2절 / 9절).
-- 출처: Viewpoint 0.1.7 공개 Lua `Viewpoint.Addons.viewState()` 의 crosshair 광선 방향(dx, dy, dz).
-- 값이 없으면(아이소·구버전 Viewpoint) 자유낙하도 A / D 회전으로 조향한다 (FreefallTurnRateKeys).
HaloDrop.CAM = {
    PITCH      = true,   -- W 활공에 카메라 피치를 반영 (FF.glide <-> FF.glideDown). false 면 W = glide 고정
    DOWN_SIGN  = -1,     -- 아래를 볼 때 dz 의 부호. 첫 런 로그(cam= dz)로 확인 전 가정값: 음수 = 아래
    DOWN_START = 0.25,   -- 시선의 아래 성분(0~1)이 이 값부터 glideDown 쪽으로 기운다 (약 15도)
    DOWN_FULL  = 0.85,   -- 이 값 이상이면 glideDown (약 58도)
    MIN_FLAT   = 0.15,   -- 시선의 수평 성분이 이보다 작으면(거의 수직 아래·위) 방향 목표를 갱신하지 않는다
}

-- ── 강하 자세 블렌드 (HaloDropPose). 2D 블렌드 좌표를 목표로 따라가는 속도와 네트워크 전송 단위.
HaloDrop.POSE = {
    RATE_X        = 6.0,   -- 좌우 기울기가 목표로 수렴하는 속도(1/초). 6 이면 약 0.17초에 63%
    RATE_Y        = 3.0,   -- 상하(감속·활공·다이브)가 목표로 수렴하는 속도(1/초). 3 이면 약 0.33초에 63%
    CANOPY_SLOW_Y = -0.4,  -- 낙하산 Ctrl(느린 하강)의 Y 목표. -1 이 S 플레어 자세
    -- 낙하산 그네 (11-23, claude/PUBG_캐노피_규칙_20261123.md 4절). 블렌드 X +-1 = 롤 35도, Y +1 = 몸 뒤로 25도
    -- 클립 끝(X +-1)이 롤 35도 (11-23 저녁 t3: 좌우 들썩임이 커서 70 -> 35 로 절반)
    CANOPY_TURN_X     = 0.43,  -- A / D 단독 선회: 약 15도
    CANOPY_TURN_FWD_X = 0.65,  -- W + A / D 선회: 약 23도. 스프링이 넘쳐도 끝(35도) 안쪽
    CANOPY_FWD_Y      = 0.6,   -- W: 몸이 뒤로 약 15도. 떼면 앞으로 넘어갔다 돌아온다 (W 반복 = 앞뒤 흔들림)
    CANOPY_RUN_Y      = 0.8,   -- Shift: 몸이 뒤로 약 20도 (캐노피를 앞으로 숙인 빠른 하강)
    CANOPY_FLARE_Y    = -1.0,  -- S 플레어: 몸 앞으로 25도, 양손 허리
    SPRING_PERIOD     = 1.3,   -- 그네 주기(초). 1.2~1.5
    SPRING_DAMP       = 0.35,  -- 감쇠비. 작을수록 넘어갔다 돌아오는 폭이 크다 (0.35 = 약 30% 넘침, 1초 뒤 약 20% 남음)
    NET_STEP      = 0.1,   -- 다른 클라에 보내는 목표값 단위 (W 중 카메라 피치처럼 연속인 값을 끊는다)
    NET_MIN_MS    = 100,   -- 목표값 전송 최소 간격(ms)
    -- 보이는 몸 방향이 이동 방향(heading)을 따라가는 속도(도/초). 다이브(몸 수직)에서는 카메라를 돌릴 때 몸이
    -- 긴 축으로 휙휙 도는 걸 막으려고 느리게 한다 (HaloDropJumper 강하 모델의 face 주석)
    FACE_RATE_FLAT  = 720,  -- 블렌드 Y 가 FACE_DIVE_START 이하(엎드림·활공): 사실상 즉시
    FACE_RATE_DIVE  = 50,   -- 블렌드 Y 가 FACE_DIVE_FULL 이상(다이브)
    FACE_DIVE_START = 1.2,
    FACE_DIVE_FULL  = 1.7,
}

-- ── 공중 칸 채우기 (HaloDropJumper airFill). 런 4 쿼터뷰 검정 화면 실험. CHUNKS 0 이면 끔 ──────
HaloDrop.AIR_FILL = {
    -- 플레이어 청크 기준 반경(청크). 1 = 3x3 청크 = ±8칸. 아이소 검정 화면 실험 때는 3(7x7)이었으나
    -- CarryZ 31 에선 7x7x31 = 1519 청크층(런 7 폭증 구간). Viewpoint 는 자체 렌더라 발밑 current 칸(엔진 로직)만
    -- 있으면 되므로 경계 넘김 대비 3x3 만 남긴다 (3x3x31 = 279 청크층, 틱당 PER_TICK 이라 약 35틱)
    CHUNKS   = 1,
    PER_TICK = 8,   -- 틱당 만드는 청크층 수 (64칸씩). 7x7x3층 = 147 -> 약 19틱 (런 5: 4 에서 올림)
}

-- ── 하강 z (HaloDropJumper visualZ). 런 6: 실제 z 가 CarryZ(3) 고정이라 고도 변화가 안 보였고, 마지막 7m
-- (엔진 낙하, z 3->0) 만 떨어지는 느낌이 났다. 그래서 뛰어내리는 z 를 CarryZ(기본 8)로 올리고 가상 고도에 맞춰
-- LOW_Z 까지 끌어내린다. 기본은 가상 고도에 비례(PROPORTIONAL), 끄면 낙하산 개방 고도에서 LOW_Z + (CarryZ-LOW_Z)*CHUTE_FRAC.
-- 11-14: CarryZ 31(엔진 상한) + 비례 매핑으로 변경. Viewpoint 1·3인칭 전용.
-- 공중 칸은 airFill 이 1~z 층에 만든다 (엔진 current 칸 탐색은 z 아래로 내려가며 첫 칸을 잡으므로
-- 칸이 없으면 지면 칸이 current 가 되고, 그 칸이 화면 밖이면 캐릭터가 안 그려진다 - IsoGridSquare.IsOnScreen).
-- CarryZ(HaloDrop.FIXED) 상한. 엔진 층 상한은 31(IsoChunkMap.isGridSquareOutOfRangeZ: -32~31).
-- 런 7: 아이소에서 16·31 은 airFill 폭증(AIR_FILL 반경 3) + 카메라가 층당 3칸 밀려 로드 창 이탈 -> 검정·프레임 정지.
-- 31 은 Viewpoint 1·3인칭 전용 전제 (아이소는 Hooks.skipIsoCellRender 로 건너뛰므로 카메라 밀림이 없다).
-- 아이소로 뛰어내리면 jump 로그에 경고가 남는다(viewCheck).
HaloDrop.CARRY_Z_MAX = 31
HaloDrop.ISO_SAFE_Z  = 12   -- 아이소에서 버티는 상한 (viewCheck 경고 기준)
HaloDrop.DESCENT_Z = {
    ENABLED    = true,
    LOW_Z      = 3,     -- 이 층 아래는 엔진 낙하 (런 3~6 검증 구간)
    -- 가상 고도 -> 실제 z 를 JumpAltitude~LOW_Z 한 직선으로 잇는다. 층당 미터가 구간마다 같아서
    -- 화면 하강 속도 비율이 실제 속도 비율(자유낙하 > 캐노피)과 같아진다.
    -- 이전 CHUTE_FRAC 0.5 는 자유낙하 500m 와 캐노피 93m 에 층을 반씩 나눠 캐노피가 화면상 더 빨랐다(0.15 vs 0.19층/초).
    PROPORTIONAL = true,
    -- PROPORTIONAL 일 때 직선 대신 로그 곡선 z = LOW + (CarryZ-LOW) * ln(1 + u/B) / ln(1 + 1/B), u = 가상 고도 비율(0~1).
    -- 11-18: 직선은 1층이 가상 약 53m 라 캐노피가 화면에서 0.15층/초로 내려오다 LOW_Z 아래(1층 2.45m)에서 20배 빨라졌다.
    -- 0.05 = 맨 아래 기울기가 실제(1층 2.45m)의 30%. 캐노피 화면 속도가 지면에 다가갈수록 0.28 -> 0.95층/초로 서서히 오른다.
    -- 대가: 고도 1200m 자유낙하 화면 속도 1.03 -> 0.40층/초, 자동 개방(270m) 때 z 7.9 -> 16.9. 0 이나 nil 이면 직선.
    CURVE_B = 0.05,
    CHUTE_FRAC   = 0.5, -- PROPORTIONAL false 일 때만: 낙하산 개방 고도에서 남겨 둘 층 비율
}

-- ── 강하 그림자 (HaloDropShadow.lua). 발밑 지면 칸에 검은 타원. 캐릭터와의 화면 간격이 남은 높이.
HaloDrop.SHADOW = {
    ENABLED      = true,
    BODY_TILES   = 0.9,    -- 자유낙하 그림자 폭 (월드 칸)
    CANOPY_TILES = 2.6,    -- 낙하산 그림자 폭 (월드 칸)
    ALPHA_FAR    = 0.18,   -- FADE_FLOORS 층 이상 높이에서의 투명도
    ALPHA_NEAR   = 0.55,   -- 지면 바로 위에서의 투명도
    FADE_FLOORS  = 8,
}

-- ── 하강 줌 (HaloDropJumper descentZoom). 런 5: 실제 z 가 끝까지 CarryZ 라 고도가 변하는 시각 단서가 없었다.
-- 뛰어내릴 때 옵션의 가장 먼 줌 레벨까지 빼고, 가상 고도가 ALT_NEAR 까지 내려오는 동안 원래 줌으로 당긴다.
-- 바닐라 Tutorial(Steps.lua SneakStep.setZoom / Tutorial1.lua setZoomLevels)과 같은 경로:
-- setZoomLevels 로 촘촘한 레벨 목록을 깔고 doZoomScroll 로 한 단계씩 옮긴다(MultiTextureFBO2 가 프레임당 0.02 씩 보간).
-- 착지·종료 때 옵션 문자열(getOptionZoomLevels1x/2x, "250;225;..." %)로 원래 목록을 되돌린다. ENABLED false 면 끔.
HaloDrop.DESCENT_ZOOM = {
    ENABLED  = false,  -- 런 6: 2.5->0.75 로 당겨도 "떠 있는" 느낌은 그대로였고, DESCENT_Z 의 z 간격 변화와 서로 상쇄된다
    STEP     = 0.05,   -- 레벨 간격(줌 배율). 작을수록 매끄럽다
    ALT_NEAR = 80,     -- 이 가상 고도(m) 아래서는 원래 줌
}

-- ── 하늘 뷰 (HaloDropSky.lua) 상수. 지도 줌은 샌드박스 SkyZoom. ─────────────────────
HaloDrop.SKY = {
    LEAD       = 90,     -- 지도 중심을 진행 방향으로 당기는 거리(타일). 기체 앞쪽이 더 보이게
    -- 11-17: 지도는 M 키 월드맵과 같은 벡터 지도를 북쪽이 위인 탑뷰(□)로 보여준다 (배그 지도처럼). false 면 이전 아이소(◇)
    TOP_DOWN   = true,
    ZOOM_MIN   = 11.0,   -- 휠 확대·축소 범위 (UIWorldMap zoom). 시작값은 FIXED.SkyZoom. 11-19: 8 -> 11 (지도 전체가 화면에 맞는 줌이 약 11.3, 그보다 멀리 빼는 건 의미 없음)
    ZOOM_MAX   = 20.0,
    ZOOM_STEP  = 0.5,    -- 휠 한 칸당 zoom 변화
    -- 11-23: 수송기는 3D 모형(C-17) 대신 배그식 2D 아이콘(media/textures/HaloDrop/plane_icon.png, 기수가 위)으로 그린다.
    --   지도 확대·축소를 따라간다: 한 변 = 기본 x 2^(zoom - SkyZoom). 너무 작아지거나 커지지 않게 자른다
    ICON_TEX   = "media/textures/HaloDrop/plane_icon.png",
    ICON_FRAC  = 0.05,   -- SkyZoom 일 때 아이콘 한 변 = 화면 짧은 변 x 이 값 (1440 기준 72px)
    ICON_MIN   = 0.9,    -- 한 변 최소 = 기본 크기 x 이 값. 11-23 런: 0.5 면 지도 전체로 축소했을 때 흰 경로선과 같은 굵기로 묻혔다
    ICON_HALO  = 1.35,   -- 아이콘 뒤에 깔 어두운 실루엣 크기 (아이콘 한 변 x 이 값). 흰 경로선·밝은 지도 위에서 윤곽을 띄운다
    ICON_HALO_A = 0.75,  -- 그 실루엣의 불투명도 (색은 검정)
    ICON_MAX   = 0.25,   -- 한 변 최대 = 화면 짧은 변 x 이 값
    ROUTE_W    = 3,      -- 경로선 두께(px)
    COVER_RATE = 2.0,    -- 구름 덮개 알파가 목표로 수렴하는 속도(1/초)
    TILE_LOADS_PER_FRAME = 2,  -- 타일 텍스처를 프레임당 몇 장까지 새로 읽을지 (히치 방지)
    TINT_NIGHT = 0.35,   -- 밤(낮 밝기 0)일 때 타일 밝기 배율
}

-- 자유낙하 바람 소리 (HaloDropJumper windSound*). 음량은 하강 속도 vz 를 따라 VOL_MIN -> VOL_MAX (FreefallSpeed 에서 최대)
HaloDrop.WIND = {
    VOL_MIN = 0.4,   -- 뛰어내린 직후(vz 0). 최대 대비 비율이다 (전체 크기가 아니다)
    VOL_MAX = 1.0,   -- 최대 하강 속도. 전체 크기는 음원에 구워 넣는다 (11-23 2차: -23 LUFS, 1차 -16.3 LUFS 보다 약 6.7dB 작음).
                     -- 크기를 바꿀 땐 이 값이 아니라 halodrop_freefall_wind.wav 자체의 음량을 고칠 것
}

-- 자유낙하 광각(어안) 연출 (HaloDropLens.lua + HaloDropClient.jar Lens). Viewpoint 1·3인칭에서만 보인다.
-- Viewpoint 설정창(Delete) 값은 건드리지 않고, 뛰어내린 뒤부터 낙하산을 펼 때까지만 화면에 덧씌운다.
-- 세기 k(0~1)는 하강 속도를 따라간다. 아래 FOV / PANINI / PULL 은 k = 1(Shift 다이브 최고 속도)일 때의 값이다.
HaloDrop.LENS = {
    ON         = true,
    -- 11-23 런: 0.5 / 0.8 / 0.6 / 0.6 은 3인칭 60 -> 86도(무입력)·94도(다이브)로 덜 극적이었다 -> 끝까지 과장
    FOV_CAP    = 140,   -- 시야각을 넓혀 갈 끝점(도). Viewpoint 상한(View.MAX_FOV)과 같다
    FOV        = 0.85,  -- 플레이어 시야각에서 FOV_CAP 까지 가는 비율. 3인칭 60 -> 무입력 약 114, 다이브 128 / 1인칭 90 -> 약 125, 133
    PANINI     = 1.0,   -- 화면을 둥글게 휘는 세기 (Viewpoint 파니니 투영, 설정 범위 0.1~1 의 최대. 플레이어가 더 세게 켜 뒀으면 그 값)
    PULL       = 0.8,   -- 시야각을 넓혀 작아진 캐릭터 크기를 카메라를 당겨 되돌리는 비율 (1 = 원래 크기 그대로, 3인칭만)
    K_NEUTRAL  = 0.8,   -- 무입력 자유낙하 속도(150 km/h)에서의 세기. 다이브 최고 속도에서 1
    -- 11-23 2차: 높을 때는 볼록(파니니) 높이감, 낮을수록·다이브할수록 파니니를 빼서 오목 쪽(가장자리 늘어남) 돌진감
    PANINI_ALT_HIGH = 1800,  -- 발밑 지면 기준 고도(m). 이 위에서는 파니니 그대로 (볼록)
    PANINI_ALT_LOW  = 700,   -- 이 아래에서는 파니니 0 (넓은 시야각 그대로 = 가장자리가 늘어나는 오목 쪽). 자동 전개는 360m
    DIVE_CUT   = 0.6,   -- Shift 다이브 최고 속도에서 볼록 정도를 이 비율만큼 깎는다
    RATE_CONVEX = 0.8,  -- 볼록 정도가 목표로 가는 속도 (초당)
    CONCAVE_FOV = 0.3,  -- 11-23 3차: 볼록이 다 빠졌을 때(c = 0) 시야각 비율에 더하는 값. 무입력 낙하(k 약 0.88)에서도 상한 140도에 닿아
                        -- 3인칭 약 119 -> 140도, 가로로는 약 157도 = 가장자리가 확실히 늘어나는 오목 쪽. 0 이면 이전 동작
    RATE_IN    = 1.5,   -- 세기가 올라가는 속도 (초당)
    RATE_OUT   = 1.0,   -- 자유낙하 중 세기가 내려가는 속도 (다이브를 풀 때)
    RATE_CHUTE = 3.0,   -- 낙하산을 편 뒤 0 까지 줄이는 속도 (약 0.3초)
}

-- 자유낙하 스피드라인 오버레이 (HaloDropSpeedLines.lua). 화면 가장자리에서 가운데로 모이는 흰 선, 가운데는 비어 있다.
-- 투명도 = ALPHA_MAX x 광각 세기 k x (HIGH_SHARE + (1 - HIGH_SHARE) x (1 - 볼록 정도 c)): 높을 땐 옅고 낮을수록·다이브할수록 진하다.
HaloDrop.SPEEDLINES = {
    ON         = true,
    TEX        = "media/textures/HaloDrop/speedline_%d.png",
    FRAMES     = 4,     -- 텍스처 장 수
    FPS        = 12,    -- 초당 장 바꾸기 횟수
    ALPHA_MAX  = 0.45,  -- 가장 진할 때 투명도
    HIGH_SHARE = 0.3,   -- 높을 때(볼록) 최대 대비 비율
}

function HaloDrop.log(msg)
    print("[HaloDrop] " .. tostring(msg))
end

-- ── 강하 고정값 (11-14: 샌드박스에서 빼고 하드코딩). 실제 높이만 엔진 상한 31층으로 타협하고
-- 나머지는 배그 실측 수치 (claude/PUBG_강하_조작명세서.md 3절 속도표 / 8절 환산, 11-16).
-- 오일리그(대기실) 탑승 구역. 월드 타일 좌표, 양끝 포함, 높이(z)는 안 본다(오일리그는 여러 층).
-- 샌드박스 OilRigOnly 가 켜져 있으면 라운드 시작 때 이 안에 있는 사람만 탄다 (11-19: 좌표 4개 샌드박스 -> 체크박스 하나).
-- 오일리그 맵 모드 위치가 바뀌면 여기만 고친다. Blue Zone 의 면제 구역 기본값(ExemptX1~Y2)도 같은 사각형이다.
HaloDrop.OIL_RIG = { x1 = 19196, y1 = 252, x2 = 19231, y2 = 292 }

HaloDrop.FIXED = {
    PlaneZ           = 31,    -- 점프 수송기 차량이 나는 실제 층 (엔진 상한)
    CarryZ           = 31,    -- 뛰어내리는 실제 층 (엔진 상한, Viewpoint 1·3인칭 전용)
    JumpAltitude     = 3000,  -- 뛰어내릴 때 가상 고도(m). 배그는 약 1500(영상 고도계 1480m)인데, 수평 속도를 0.35배로 줄인 만큼 활공 거리를 벌려고 2배로 올렸다 (11-19 t3). 무입력 자유낙하 약 63초, W 활공 약 650칸
    AutoOpenAltitude = 360,   -- 낙하산 자동 개방이 시작되는 높이(m). 발밑 바닥(지면·옥상) 기준. 실제 전개는 CHUTE_OPEN_MS 뒤
    FreefallSpeed    = 65.0,  -- 자유낙하 최대 하강(m/초) = Shift 다이브 234km/h
    CanopySpeed      = 6.9,   -- 낙하산 무입력 하강(m/초) = 25km/h (이전 15.0 은 옛 웹 자료 54km/h)
    -- 수평 속도는 배그 수치(34 / 13)를 못 쓴다: 안티치트 한도(차량 밖 1초 평균 20타일/초) + 청크 스트리밍.
    -- 런 8(11-14): 16 / 13 으로 올렸더니 다음 칸이 안 올라와 수평 이동이 10초·4초씩 멈췄다 -> 런 6 값으로 되돌림
    FreefallSteer    = 12.0,  -- 자유낙하 최대 수평(타일/초) = 배그 122km/h x k(0.35). 올리면 모든 수평 값이 같이 커진다
    CanopySteer      = 7.0,   -- 낙하산 W 수평(타일/초) = 배그 72km/h x k(0.35). FreefallSteer 를 올리면 같은 비율로 올릴 것
    -- 관성 계수(1/초): v += (목표 - v) * Accel * dt (명세서 8.1절)
    FreefallAccel    = 1.0,   -- 자유낙하: 4~5초에 정속 (Shift 174 -> 234 km/h 약 4초)
    CanopyAccel      = 0.6,   -- 낙하산 하강 수렴 빠르기. 0.3(배그 Shift 약 10초)은 W·Shift 를 눌러도 굼떠서 올림 (11-23)
    CanopyAccelH     = 1.0,   -- 낙하산 수평: 배그 실측 없음(임의). 하강과 같은 0.3 이면 W 를 눌러도 3초 넘게 굼뜨다
    ChuteDecel       = 4.0,   -- 전개 직후 감속: 150 -> 약 27 km/h 가 1초 (배그 150 -> 18 약 1초, 232 -> 28 약 2초). 끝 기준은 CHUTE_DECEL_END
    -- 회전(도/초)
    FreefallTurnRate     = 180.0,  -- 자유낙하: 몸이 카메라 방향을 따라가는 속도
    FreefallTurnRateKeys = 120.0,  -- 자유낙하: 카메라 값이 없을 때 A / D 회전 속도 (이전 샌드박스 기본값)
    CanopyTurnRate       = 45.0,   -- 낙하산 A / D 선회 (한 바퀴 8초)
    CanopyTurnRateFwd    = 60.0,   -- 낙하산 W + A / D 선회 (한 바퀴 6초)
    -- 11-19 t3: 아래 둘은 샌드박스였는데 의미를 알기 어렵고 건드릴 일이 없어 고정값으로 옮겼다 (기본값 그대로)
    PitchRate        = 2.0,   -- W / S 자세 기울기 변화 속도(1/초). 2.0 = 0.5초에 완전히 기울고, 떼면 같은 속도로 돌아온다
    JumpWaitMax      = 45,    -- 점프 키를 누른 뒤 구름 연출 속에서 발밑 지형·수송기 로드를 기다리는 최대 시간(초). 넘으면 그냥 떨어진다
    -- 하늘 뷰 (11-19 t3: 샌드박스에서 뺐다. 줌은 탑승 중 휠로 바뀌고, 타일 배율은 사진 타일이 없어 지금은 효과가 없다)
    SkyZoom          = 12.5,  -- 하늘 뷰 시작 줌 (UIWorldMap zoom, 1 올릴 때마다 2배 확대). 지도 전체가 화면에 맞는 값이 약 11.3(2560x1440),
                              -- 12.5 면 가로 약 10,000칸이 보인다 (이전 14 는 약 4,300칸이라 너무 가까웠다). 수송기 아이콘 크기의 기준이기도 하다
    SkyScale         = 1.5,   -- 사진 타일(HaloDropTiles.lua) 배율, 월드 1칸 = 화면 px. 타일 파일이 없으면 쓰이지 않는다(벡터 지도로 대체)
}

-- 고정값이 있으면 그 값, 아니면 샌드박스 옵션 (등록된 옵션은 로드 후 항상 값이 있다 - fallback 불필요)
function HaloDrop.opt(name)
    local v = HaloDrop.FIXED[name]
    if v ~= nil then return v end
    return SandboxVars.HaloDrop[name]
end

-- 라운드 멤버 키. MP 는 onlineID, SP 는 로컬 플레이어 하나뿐이라 무엇이든 일관되면 된다.
function HaloDrop.playerKey(p)
    return tostring(p:getOnlineID())
end
