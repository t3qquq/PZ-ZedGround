-- Halo Drop 하늘 뷰 (클라): 탑승~점프 대기 동안 월드 대신 지도 위의 수송기를 보여준다
--
-- 왜: 48 타일/초 비행은 클라 청크 스트리밍이 못 따라와 월드가 검게 나온다(11-06 런 3). 비행 구간은
--   서버 Flight(경로·시각)로 위치만 계산하고 화면은 UI 로 그린다 (Wingsmith 하늘 뷰와 같은 구조,
--   claude/Wingsmith_하늘뷰_분석.md).
-- 구조 (런 4 에서 재편: 전체화면 요소가 인벤토리·체력·HUD 를 전부 가렸다)
--   SkyRoot  전체화면 ISUIElement 하나만 UIManager 에 넣고 backMost() 로 맨 뒤에 둔다(바닐라 FishingManager 와 같은 방식).
--            바닐라 UI 는 그 위에 그대로 그려진다. 지도(수송기 아이콘 포함)는 이 요소의 자식, 구름 덮개는 root:render 에서 맨 마지막.
--   SkyTiles pzmap2dzi 아이소 렌더 타일(HaloDropMap.levels)을 직접 깐다 (Wingsmith 식). 배율 = 샌드박스 SkyScale(px/칸).
--            타일이 없으면(HaloDropTiles.lua 부재) 아래 SkyMap 으로 대체.
--   SkyMap   UIWorldMap(바닐라 월드맵 벡터 렌더러 = M 키 지도). 11-17: SKY.TOP_DOWN 이면 북쪽이 위인 탑뷰(□),
--            경로 A->B 를 화살표로 그리고 마우스 휠로 확대·축소. 열린 동안 바닐라 미니맵은 숨겼다가 닫을 때 탑승 직전 상태로 되돌린다.
--            11-18: 왼쪽 드래그로 지도를 옮긴다(S.panX/Y = 수송기 기준 중심 오프셋, 타일). 지도는 계속 수송기를 따라가고
--            옮긴 만큼의 차이는 유지된다. 오른쪽 클릭이면 오프셋을 지운다.
--   수송기  11-23: 3D 모형(ISUI3DScene + C-17 차량 스크립트) 대신 배그식 2D 아이콘을 지도 요소가 직접 그린다 (drawPlaneIcon).
--            기수는 진행 방향, 크기는 지도 zoom 을 따라간다: 한 변 = 기본 x 2^(지도 zoom - SkyZoom)
--            (UIWorldMap 은 zoom 1 당 2배, WorldMapRenderer.zoomMult = 2^zoomF).
--   HUD 는 HaloDropHUD 가 OnPostUIDraw 로 그린다 (OnPreUIDraw 는 UI 요소보다 먼저라 이 요소에 가려진다).
-- 호출: HaloDropJumper 가 board() 에서 open, 점프 수송기 확보(onPlaneAcquired) 또는 자유낙하 시작 때 close.
--   점프 대기(JUMPING) 중엔 setCover(1).
-- 시점: Viewpoint(1·3인칭) 여부와 무관하게 UI 로 그린다 (런 4 라운드 3 에서 Viewpoint 켠 채로 정상 표시 확인).

if isServer() then return end

require "HaloDropConfig"
require "HaloDropFlight"
require "HaloDropMap"
require "ISUI/ISUIElement"
require "ISUI/Maps/ISMapDefinitions"

HaloDropSky = {}
local S = { open = false, cover = 0, coverTarget = 0 }
HaloDropSky.S = S

local function log(msg) HaloDrop.log(msg) end
local K = HaloDrop.SKY

local function screenSize()
    local core = getCore()
    return core:getScreenWidth(), core:getScreenHeight()
end

-- ── 수송기 아이콘 ─────────────────────────────────────────────────────────────
local iconTex = nil          -- nil = 아직 안 읽음, false = 없음
local function planeIcon()
    if iconTex == nil then
        local ok, tx = pcall(function() return getTexture(K.ICON_TEX) end)
        iconTex = (ok and tx) or false
        if not iconTex then log("sky: plane icon missing " .. tostring(K.ICON_TEX) .. (ok and "" or (" err=" .. tostring(tx)))) end
    end
    return iconTex or nil
end

-- 지도 zoom 에 맞춘 아이콘 한 변(px). zoom 이 nil 이면(사진 타일) 기본 크기
local function iconSize(zoom)
    local sw, sh = screenSize()
    local short = math.min(sw, sh)
    local base = short * K.ICON_FRAC
    if not zoom then return base end
    local w = base * 2 ^ (zoom - HaloDrop.opt("SkyZoom"))
    local wMin, wMax = base * K.ICON_MIN, short * K.ICON_MAX
    if w < wMin then w = wMin elseif w > wMax then w = wMax end
    return w
end

-- 요소 좌표 (x,y) 를 중심으로, 화면상 진행 방향 단위벡터 (ux,uy) 로 기수를 돌려 한 변 size 로 그린다.
-- 텍스처는 기수가 위(-y). drawTextureAllPoint(DrawTexture 12인자)는 요소 오프셋을 안 더하므로 절대좌표로 넘긴다
-- 11-23: 흰 아이콘이 흰 경로선 위에서 안 보여 같은 모양을 검게, 더 크게 먼저 깐다 (K.ICON_HALO / ICON_HALO_A)
local function drawPlaneQuad(el, tex, cx, cy, ux, uy, size, r, g, b, a)
    local h = size / 2
    local fx, fy = ux * h, uy * h          -- 기수 쪽
    local rx, ry = -uy * h, ux * h         -- 오른쪽 날개 쪽
    el:drawTextureAllPoint(tex,
        cx + fx - rx, cy + fy - ry,        -- 왼쪽 위
        cx + fx + rx, cy + fy + ry,        -- 오른쪽 위
        cx - fx + rx, cy - fy + ry,        -- 오른쪽 아래
        cx - fx - rx, cy - fy - ry,        -- 왼쪽 아래
        r, g, b, a)
end

local function drawPlaneIcon(el, x, y, ux, uy, size)
    local tex = planeIcon()
    if not tex then return end
    local cx, cy = el:getAbsoluteX() + x, el:getAbsoluteY() + y
    if K.ICON_HALO and K.ICON_HALO > 1 then
        drawPlaneQuad(el, tex, cx, cy, ux, uy, size * K.ICON_HALO, 0, 0, 0, K.ICON_HALO_A or 0.75)
    end
    drawPlaneQuad(el, tex, cx, cy, ux, uy, size, 1, 1, 1, 1)
    local now = getTimestampMs()
    if el.iconSize ~= math.floor(size + 0.5) and (not el.iconLogAt or now >= el.iconLogAt) then
        el.iconLogAt = now + 1000
        el.iconSize = math.floor(size + 0.5)
        log(string.format("sky: plane icon %dpx at (%.0f,%.0f)", el.iconSize, x, y))
    end
end

-- 수송기 위치·진행 방향을 요소 좌표로 바꿔 아이콘을 그린다. toUI(wx, wy) -> 요소 x, y
local function drawPlaneAt(el, toUI, size)
    if not S.px or not S.heading then return end
    local r = math.rad(S.heading)
    local x, y = toUI(S.px, S.py)
    local x2, y2 = toUI(S.px + math.cos(r) * 100, S.py + math.sin(r) * 100)
    local dx, dy = x2 - x, y2 - y
    local len = math.sqrt(dx * dx + dy * dy)
    if len < 1e-6 then return end
    drawPlaneIcon(el, x, y, dx / len, dy / len, size)
end

-- ── 타일 레이어 (Wingsmith 식 직접 그리기) ──────────────────────────────────────
local SkyTiles = ISUIElement:derive("HaloDropSkyTiles")

function SkyTiles:new()
    local w, h = screenSize()
    local o = ISUIElement:new(0, 0, w, h)
    setmetatable(o, self)
    self.__index = self
    o.tex = {}        -- key -> Texture | false(없음)
    o.loads = 0
    o.drawn, o.missing = 0, 0
    o.nextLog = nil
    return o
end

-- 텍스처는 바닐라 getTexture 가 캐시한다(해제 없음). 프레임당 새로 읽는 수를 제한해 히치를 줄인다.
function SkyTiles:tile(L, c, r)
    local key = L.id .. ":" .. c .. "_" .. r
    local t = self.tex[key]
    if t ~= nil then return t or nil end
    if not HaloDropMap.has(L, c, r) then
        self.tex[key] = false
        return nil
    end
    if self.loads >= K.TILE_LOADS_PER_FRAME then return nil end
    self.loads = self.loads + 1
    local path = L.path .. c .. "_" .. r .. ".jpg"
    local ok, tx = pcall(function() return getTexture(path) end)
    if ok and tx then
        self.tex[key] = tx
        return tx
    end
    self.tex[key] = false
    log("sky tiles: missing " .. path .. (ok and "" or (" err=" .. tostring(tx))))
    return nil
end

function SkyTiles:prerender()
    self.loads = 0
    local scale = HaloDrop.opt("SkyScale")
    local L = HaloDropMap.levelFor(scale)
    if not L or not S.cx then
        self:drawRect(0, 0, self.width, self.height, 1, HaloDropMap.BG.r, HaloDropMap.BG.g, HaloDropMap.BG.b)
        return
    end
    local k = scale / L.px                     -- 타일 px -> 화면 px
    local cxp, cyp = HaloDropMap.toMap(L, S.cx, S.cy)
    local ax, ay = self.width / 2, self.height / 2
    -- 화면 (0,0) 에 해당하는 타일 픽셀 좌표
    local mx0, my0 = cxp - ax / k, cyp - ay / k
    local c0, r0 = math.floor(mx0 / HaloDropMap.TILE), math.floor(my0 / HaloDropMap.TILE)
    local c1 = math.floor((mx0 + self.width / k) / HaloDropMap.TILE)
    local r1 = math.floor((my0 + self.height / k) / HaloDropMap.TILE)
    local tint = S.tint or 1
    self:drawRect(0, 0, self.width, self.height, 1, HaloDropMap.BG.r * tint, HaloDropMap.BG.g * tint, HaloDropMap.BG.b * tint)
    local drawn, missing = 0, 0
    for r = r0, r1 do
        for c = c0, c1 do
            local t = self:tile(L, c, r)
            if t then
                local x = math.floor((c * HaloDropMap.TILE - mx0) * k + 0.5)
                local y = math.floor((r * HaloDropMap.TILE - my0) * k + 0.5)
                local x2 = math.floor(((c + 1) * HaloDropMap.TILE - mx0) * k + 0.5)
                local y2 = math.floor(((r + 1) * HaloDropMap.TILE - my0) * k + 0.5)
                self:drawTextureScaled(t, x, y, x2 - x, y2 - y, 1, tint, tint, tint)
                drawn = drawn + 1
            elseif HaloDropMap.has(L, c, r) then
                missing = missing + 1
            end
        end
    end
    drawPlaneAt(self, function(wx, wy)
        local mx, my = HaloDropMap.toMap(L, wx, wy)
        return (mx - mx0) * k, (my - my0) * k
    end, iconSize(nil))
    self.drawn, self.missing, self.level = drawn, missing, L
    local now = getTimestampMs()
    if not self.nextLog or now >= self.nextLog then
        self.nextLog = now + 5000
        log(string.format("sky tiles: level=%s scale=%.2f k=%.3f center=(%.0f,%.0f) tiles c%d..%d r%d..%d drawn=%d pending=%d",
            tostring(L.id), scale, k, S.cx, S.cy, c0, c1, r0, r1, drawn, missing))
    end
end

-- ── 지도 레이어 (벡터 폴백) ───────────────────────────────────────────────────────────────
local SkyMap = ISUIElement:derive("HaloDropSkyMap")

function SkyMap:new()
    local w, h = screenSize()
    local o = ISUIElement:new(0, 0, w, h)
    setmetatable(o, self)
    self.__index = self
    return o
end

function SkyMap:instantiate()
    self.javaObject = UIWorldMap.new(self)
    self.mapAPI = self.javaObject:getAPIv3()
    self.mapAPI:setMapItem(MapItem.getSingleton())
    self.mapAPI:setMaxZoom(24)
    self.javaObject:setX(self.x)
    self.javaObject:setY(self.y)
    self.javaObject:setWidth(self.width)
    self.javaObject:setHeight(self.height)
    -- 바닐라 미니맵(ISMiniMap.lua:710~)과 같은 순서로 맵 데이터를 넣는다: 모드 맵(오일리그) → 바닐라
    local dirs = getLotDirectories()
    local nData = 0
    for i = 1, dirs:size() do
        local dir = "media/maps/" .. dirs:get(i - 1)
        local file = dir .. "/worldmap.xml"
        if fileExists(file) then
            self.mapAPI:addData(file)
            nData = nData + 1
        end
        self.mapAPI:endDirectoryData()
        self.mapAPI:addImages(dir)
    end
    self.mapAPI:setBoundsFromWorld()
    MapUtils.initDefaultStyleV3(self)
    self.mapAPI:setBoolean("Isometric", not K.TOP_DOWN)
    self.mapAPI:setBoolean("HideUnvisited", false)
    self.mapAPI:setBoolean("Players", false)
    self.mapAPI:setBoolean("RemotePlayers", false)
    self.mapAPI:setBoolean("PlayerNames", false)
    self.mapAPI:setBoolean("Symbols", false)
    self.mapAPI:setBoolean("MiniMapSymbols", false)
    self.mapAPI:setBoolean("WorldBounds", false)
    self.mapAPI:setZoom(HaloDrop.opt("SkyZoom"))
    log(string.format("sky: map ready data=%d zoom=%.1f iso=%s", nData, HaloDrop.opt("SkyZoom"), tostring(not K.TOP_DOWN)))
end

-- 지도 중심 = 수송기 앞쪽(S.cx, S.cy) + 드래그 오프셋
local function mapCenter()
    return S.cx + (S.panX or 0), S.cy + (S.panY or 0)
end

function SkyMap:prerender()
    if S.cx and S.cy then
        self.mapAPI:centerOn(mapCenter())
    end
end

-- 왼쪽 드래그: 바닐라 MapSpawnSelect.lua 와 같은 방식(시작 시점의 zoom·중심으로 마우스 아래 월드 좌표 차이를 구한다)
-- 좌표는 getMouseX/Y 로 읽는다
function SkyMap:onMouseDown(x, y)
    if not S.cx then return true end
    local mx, my = self:getMouseX(), self:getMouseY()
    local cx, cy = mapCenter()
    local zoom = self.mapAPI:getZoomF()
    self.drag = {
        mx = mx, my = my, moved = false, cx = cx, cy = cy, zoom = zoom,
        wx = self.mapAPI:uiToWorldX(mx, my, zoom, cx, cy), wy = self.mapAPI:uiToWorldY(mx, my, zoom, cx, cy),
        panX = S.panX or 0, panY = S.panY or 0,
    }
    return true
end

function SkyMap:onMouseMove(dx, dy)
    local d = self.drag
    if not d then return true end
    local mx, my = self:getMouseX(), self:getMouseY()
    if not d.moved and math.abs(mx - d.mx) <= 4 and math.abs(my - d.my) <= 4 then return true end
    d.moved = true
    local wx = self.mapAPI:uiToWorldX(mx, my, d.zoom, d.cx, d.cy)
    local wy = self.mapAPI:uiToWorldY(mx, my, d.zoom, d.cx, d.cy)
    S.panX = d.panX + (d.wx - wx)
    S.panY = d.panY + (d.wy - wy)
    if S.cx then self.mapAPI:centerOn(mapCenter()) end
    return true
end

function SkyMap:onMouseMoveOutside(dx, dy)
    return self:onMouseMove(dx, dy)
end

function SkyMap:onMouseUp(x, y)
    local d = self.drag
    self.drag = nil
    if d and d.moved then
        log(string.format("sky: map dragged, offset from plane=(%.0f,%.0f) tiles", S.panX or 0, S.panY or 0))
    end
    return true
end

function SkyMap:onMouseUpOutside(x, y)
    return self:onMouseUp(x, y)
end

-- 오른쪽 클릭: 다시 수송기 기준으로
function SkyMap:onRightMouseUp(x, y)
    if (S.panX or 0) ~= 0 or (S.panY or 0) ~= 0 then
        S.panX, S.panY = 0, 0
        if S.cx then self.mapAPI:centerOn(mapCenter()) end
        log("sky: map offset reset (right click)")
    end
    return true
end

-- 점 (ax,ay) 에서 단위벡터 (ux,uy) 방향 길이 len 인 선분이 사각형 안에 있는 거리 구간 [d0, d1]. 안 걸치면 nil
local function clipToRect(ax, ay, ux, uy, len, x0, y0, x1, y1)
    local t0, t1 = 0, len
    local function edge(p, q)
        if math.abs(p) < 1e-9 then return q >= 0 end
        local r = q / p
        if p < 0 then
            if r > t1 then return false end
            if r > t0 then t0 = r end
        else
            if r < t0 then return false end
            if r < t1 then t1 = r end
        end
        return true
    end
    if edge(-ux, ax - x0) and edge(ux, x1 - ax) and edge(-uy, ay - y0) and edge(uy, y1 - ay) then
        return t0, t1
    end
    return nil
end

-- 경로 A->B: 지나온 구간은 흐리게, 남은 구간은 진하게, 끝(B)과 중간마다 화살촉
local function arrowHead(el, x, y, ux, uy, size, a, r, g, b)
    local px, py = -uy, ux
    local bx, by = x - ux * size, y - uy * size
    el:drawLine(nil, x, y, bx + px * size * 0.6, by + py * size * 0.6, K.ROUTE_W, a, r, g, b)
    el:drawLine(nil, x, y, bx - px * size * 0.6, by - py * size * 0.6, K.ROUTE_W, a, r, g, b)
end

function SkyMap:render()
    self:renderRoute()
    -- 수송기는 경로 위에 그린다
    local api = self.mapAPI
    drawPlaneAt(self, function(wx, wy) return api:worldToUIX(wx, wy), api:worldToUIY(wx, wy) end, iconSize(api:getZoomF()))
end

function SkyMap:renderRoute()
    local fl = HaloDrop.C and HaloDrop.C.fl
    if not fl or not S.px then return end
    local api = self.mapAPI
    local ax, ay = api:worldToUIX(fl.ax, fl.ay), api:worldToUIY(fl.ax, fl.ay)
    local bx, by = api:worldToUIX(fl.bx, fl.by), api:worldToUIY(fl.bx, fl.by)
    local px, py = api:worldToUIX(S.px, S.py), api:worldToUIY(S.px, S.py)
    local dx, dy = bx - ax, by - ay
    local len = math.sqrt(dx * dx + dy * dy)
    if len < 1 then return end
    local ux, uy = dx / len, dy / len
    self:drawLine(nil, ax, ay, px, py, K.ROUTE_W, 0.35, 1, 1, 1)
    self:drawLine(nil, px, py, bx, by, K.ROUTE_W, 0.95, 1, 1, 1)
    local size = 18
    local step = 220                                  -- 화면 px 마다 진행 방향 화살촉
    -- 화면 안에 걸리는 구간만 훑는다 (확대하면 경로가 화면의 수백 배라 전 구간을 돌면 프레임마다 수천 번 그린다)
    local d0, d1 = clipToRect(ax, ay, ux, uy, len, -size, -size, self.width + size, self.height + size)
    if d0 then
        local dp = (px - ax) * ux + (py - ay) * uy      -- 수송기 위치. 화살촉은 그 앞쪽만
        local first = dp + step
        if first < d0 then first = dp + math.ceil((d0 - dp) / step) * step end
        local d = first
        while d <= d1 and d < len - size do
            arrowHead(self, ax + ux * d, ay + uy * d, ux, uy, size * 0.7, 0.95, 1, 1, 1)
            d = d + step
        end
    end
    if bx > -size and bx < self.width + size and by > -size and by < self.height + size then
        arrowHead(self, bx, by, ux, uy, size, 0.95, 1, 0.85, 0.2)
    end
end

function SkyMap:onMouseWheelZoom(del)
    local z = self.mapAPI:getZoomF() - del * K.ZOOM_STEP
    if z < K.ZOOM_MIN then z = K.ZOOM_MIN elseif z > K.ZOOM_MAX then z = K.ZOOM_MAX end
    self.mapAPI:setZoom(z)
    if S.cx and S.cy then self.mapAPI:centerOn(mapCenter()) end
    return true
end

-- ── 루트 (UIManager 에 들어가는 유일한 요소, 맨 뒤) ──────────────────────────────────
local SkyRoot = ISUIElement:derive("HaloDropSkyRoot")

function SkyRoot:new()
    local w, h = screenSize()
    local o = ISUIElement:new(0, 0, w, h)
    setmetatable(o, self)
    self.__index = self
    return o
end

-- 바닐라 미니맵: 열 때 보이던 상태를 기억해 숨기고, 닫을 때 그 상태로 되돌린다 (서버 설정은 건드리지 않는다)
local function miniMap()
    local ok, mm = pcall(function() return getPlayerMiniMap(0) end)
    if ok then return mm end
    return nil
end

local function miniMapHide(first)
    local mm = miniMap()
    if not mm then return end
    local vis = false
    pcall(function() vis = mm:isReallyVisible() == true end)
    if vis then
        pcall(function() mm:removeFromUIManager() end)
        if first then
            S.miniMapWasVisible = true
            log("sky: minimap hidden (was visible)")
        elseif not S.miniMapWasVisible then
            -- 탑승 중에 키로 켰다: 강하 후엔 켜진 상태로 돌려준다
            S.miniMapWasVisible = true
            log("sky: minimap re-hidden (turned on while aboard)")
        end
    end
end

local function miniMapRestore()
    if not S.miniMapWasVisible then return end
    S.miniMapWasVisible = nil
    local mm = miniMap()
    if not mm then return end
    local ok, err = pcall(function()
        if not mm:isReallyVisible() then mm:addToUIManager() end
    end)
    log("sky: minimap restored ok=" .. tostring(ok) .. (ok and "" or (" err=" .. tostring(err))))
end

function SkyRoot:prerender()
    -- 열어 둔 사이 바닐라 ISWorldMap 이 닫히면서 setDrawWorld(true) 를 부를 수 있어 매 프레임 다시 끈다
    if S.open then
        pcall(function() getWorld():setDrawWorld(false) end)
        miniMapHide(false)
    end
end

-- 자식(지도·수송기 아이콘)을 다 그린 뒤에 불린다 -> 구름 덮개는 아이콘까지 덮는다
function SkyRoot:render()
    if S.cover > 0.002 then
        self:drawRect(0, 0, self.width, self.height, 0.92 * S.cover, 0.86, 0.88, 0.92)
    end
end

-- 루트·타일 레이어는 마우스를 전부 먹는다 (월드 클릭으로 새지 않게). 벡터 지도는 드래그·휠
local function eatMouse(cls)
    cls.onMouseDown = function() return true end
    cls.onMouseUp = function() return true end
    cls.onMouseMove = function() return true end
    cls.onRightMouseDown = function() return true end
    cls.onRightMouseUp = function() return true end
    cls.onMouseWheel = function() return true end
end
eatMouse(SkyRoot)
eatMouse(SkyTiles)
SkyMap.onMouseWheel = SkyMap.onMouseWheelZoom

-- ── 공개 API ──────────────────────────────────────────────────────────────────
function HaloDropSky.isOpen() return S.open end

function HaloDropSky.open()
    if S.open then return end
    if not HaloDrop.opt("SkyView") then
        log("sky: disabled by sandbox SkyView")
        return
    end
    S.open = true
    S.cover, S.coverTarget = 0, 0
    S.cx, S.cy, S.heading, S.px, S.py = nil, nil, nil, nil, nil
    S.panX, S.panY = 0, 0
    S.miniMapWasVisible = nil
    miniMapHide(true)
    local ok, err = pcall(function()
        getWorld():setDrawWorld(false)
        S.root = SkyRoot:new()
        S.root:initialise()
        S.root:instantiate()
        if HaloDropMap.hasTiles() then
            S.map = SkyTiles:new()
            S.mode = "tiles"
        else
            S.map = SkyMap:new()
            S.mode = "vector"
        end
        S.map:initialise()
        S.map:instantiate()
        S.root:addChild(S.map)
        S.root:backMost()
        S.root:addToUIManager()
        S.root:setVisible(true)
    end)
    if not ok then
        log("sky: open FAILED err=" .. tostring(err))
        HaloDropSky.close("open failed")
        return
    end
    log("sky: opened (world drawing off, map=" .. tostring(S.mode) .. ", backmost, plane=2d icon)")
end

function HaloDropSky.close(reason)
    if not S.open and not S.root then return end
    S.open = false
    if S.root then
        pcall(function() S.root:setVisible(false) end)
        pcall(function() S.root:removeFromUIManager() end)
    end
    S.root, S.map = nil, nil
    miniMapRestore()
    local wm = ISWorldMap_instance
    local mapOpen = false
    pcall(function() mapOpen = wm ~= nil and wm:isVisible() == true end)
    if not mapOpen then pcall(function() getWorld():setDrawWorld(true) end) end
    log("sky: closed (" .. tostring(reason) .. ", world drawing " .. (mapOpen and "left to world map" or "on") .. ")")
end

-- 매 틱: 경로 위치·기수. x,y = 경로 위치(타일), headingDeg = 진행 방향(월드 +x = 0, +y = 90)
function HaloDropSky.update(x, y, headingDeg, dt)
    if not S.open then return end
    -- 앞쪽을 더 보이게 중심을 진행 방향으로 조금 당긴다
    local r = math.rad(headingDeg)
    S.px, S.py = x, y
    S.cx = x + math.cos(r) * K.LEAD
    S.cy = y + math.sin(r) * K.LEAD
    S.heading = headingDeg                        -- 아이콘 기수 방향
    -- 밤이면 타일을 어둡게 (바닐라 낮 밝기 0~1 을 TINT_NIGHT~1 로)
    local okD, day = pcall(function() return getClimateManager():getDayLightStrength() end)
    if okD and type(day) == "number" then
        S.tint = K.TINT_NIGHT + (1 - K.TINT_NIGHT) * math.max(0, math.min(1, day))
    end
    -- 구름 덮개 서서히
    local k = (dt or 0) * K.COVER_RATE
    if k > 1 then k = 1 end
    S.cover = S.cover + (S.coverTarget - S.cover) * k
end

function HaloDropSky.setCover(v)
    S.coverTarget = v
end

function HaloDropSky.setZoom(z)
    if S.map and S.map.mapAPI then pcall(function() S.map.mapAPI:setZoom(z) end) end
end

-- 라운드가 끝나거나 죽으면 반드시 월드로 돌아오게
Events.OnPlayerDeath.Add(function(p)
    if p == getPlayer() and S.open then HaloDropSky.close("death") end
end)
