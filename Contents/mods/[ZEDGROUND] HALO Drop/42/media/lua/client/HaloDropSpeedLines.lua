-- Halo Drop: 자유낙하 스피드라인 오버레이 (11-23)
--
-- 화면 가장자리에서 가운데로 모이는 흰 선 텍스처 FRAMES 장(media/textures/HaloDrop/speedline_N.png, 가운데는 비어 있음)을
-- 초당 FPS 번 무작위로 바꿔 가며 화면 전체에 깐다. 매번 좌우·상하 뒤집기도 무작위라 같은 그림이 반복돼 보이지 않는다.
-- 투명도는 HaloDropLens 가 매 틱 set(alpha) 로 준다 (광각 세기 k 와 볼록 정도 c 로 계산). 0 이면 안 그린다.
-- 전체화면 ISUIElement 하나를 backMost() 로 맨 뒤에 둔다: 나침반·타이머·핫바 같은 다른 UI 가 선 위에 그려진다.
-- 마우스는 먹지 않는다. 시작·끝은 HaloDropLens.start / stop 이 부른다.

if isServer() then return end

require "HaloDropConfig"
require "ISUI/ISUIElement"

HaloDropSpeedLines = HaloDropSpeedLines or {}

local UI = ISUIElement:derive("HaloDropSpeedLinesUI")
local S = { el = nil, tex = nil, frame = 1, nextAt = 0, flipX = false, flipY = false, alpha = 0, drawn = 0, frames = 0 }

local function log(msg) HaloDrop.log(msg) end

local function textures()
    if S.tex ~= nil then return S.tex end
    local C = HaloDrop.SPEEDLINES
    local list = {}
    for i = 1, C.FRAMES do
        local path = string.format(C.TEX, i)
        local ok, t = pcall(function() return getTexture(path) end)
        if ok and t then list[#list + 1] = t else log("speedlines: texture missing " .. path) end
    end
    if #list == 0 then
        S.tex = false
        log("speedlines: no textures, overlay off")
    else
        S.tex = list
        log(string.format("speedlines: %d textures loaded", #list))
    end
    return S.tex
end

function UI:new()
    local o = ISUIElement.new(self, 0, 0, getCore():getScreenWidth(), getCore():getScreenHeight())
    o.anchorRight = true
    o.anchorBottom = true
    return o
end

function UI:instantiate()
    ISUIElement.instantiate(self)
    self.javaObject:setConsumeMouseEvents(false)
end

function UI:render()
    local a = S.alpha
    if a <= 0.002 then return end
    local list = textures()
    if not list then return end
    local w, h = getCore():getScreenWidth(), getCore():getScreenHeight()
    if self.width ~= w or self.height ~= h then
        self:setWidth(w)
        self:setHeight(h)
    end
    local C = HaloDrop.SPEEDLINES
    local now = getTimestampMs()
    if now >= S.nextAt then
        local n = #list
        if n > 1 then
            local f = ZombRand(n - 1) + 1          -- 지금 장을 뺀 나머지 중 하나
            if f >= S.frame then f = f + 1 end
            S.frame = f
        else
            S.frame = 1
        end
        S.flipX = ZombRand(2) == 0
        S.flipY = ZombRand(2) == 0
        S.nextAt = now + math.floor(1000 / C.FPS)
        S.frames = S.frames + 1
    end
    local tex = list[S.frame]
    local x0, y0 = self:getAbsoluteX(), self:getAbsoluteY()
    local l, r, t, b = x0, x0 + w, y0, y0 + h
    if S.flipX then l, r = r, l end
    if S.flipY then t, b = b, t end
    -- drawTextureAllPoint: 왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래 (절대 좌표). 모서리를 바꿔 넣어 뒤집는다
    self:drawTextureAllPoint(tex, l, t, r, t, r, b, l, b, 1, 1, 1, a)
    S.drawn = S.drawn + 1
end

function HaloDropSpeedLines.start()
    local C = HaloDrop.SPEEDLINES
    if not C or not C.ON then return end
    S.alpha = 0
    S.drawn = 0
    S.frames = 0
    if S.el then return end
    local ok, err = pcall(function()
        local el = UI:new()
        el:initialise()
        el:instantiate()
        el:addToUIManager()
        el:backMost()
        S.el = el
    end)
    log("speedlines: start " .. (ok and "ok" or ("FAILED err=" .. tostring(err))) .. string.format(" (frames=%d fps=%d alphaMax=%.2f)", C.FRAMES, C.FPS, C.ALPHA_MAX))
end

function HaloDropSpeedLines.set(alpha)
    if alpha < 0 then alpha = 0 elseif alpha > 1 then alpha = 1 end
    S.alpha = alpha
end

function HaloDropSpeedLines.stop(why)
    S.alpha = 0
    if not S.el then return end
    local el = S.el
    S.el = nil
    pcall(function() el:removeFromUIManager() end)
    log(string.format("speedlines: stop (%s) drawn=%d frames, switched %d times", tostring(why), S.drawn, S.frames))
end
