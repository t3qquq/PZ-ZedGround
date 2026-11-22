--[[ ZEDGROUND HUD : 방독면 필터 잔량 표시 (핫바 위)
  원본: Mask Filter Indicator by movinow
  https://steamcommunity.com/sharedfiles/filedetails/?id=3832759095
  제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 이 파일을 다른 곳에 재배포하지 말 것.
  원본과 다른 점: 전역·파일 이름(MaskFilterIndicator* -> ZedHUDMask*), 모드 옵션은 ZedHUDOptions.lua 로 합침,
  봉쇄 구역 독가스로 필터가 닳을 때도 막대가 깜빡인다, 로그 추가. 그 밖의 동작·수치는 원본 그대로.
]]

require "ISUI/ISPanel"
require "ZedHUDConfig"

ZedHUDMaskHUD = ISPanel:derive("ZedHUDMaskHUD")

local BLOCKS = 10
local BLOCK_H = 12
local PIP_H = 5
local GAP = 2
local PAD = 4
local ICON = 24
local MASK_H = BLOCK_H + GAP + PIP_H
local DEFAULT_W = 240
local REFRESH_TICKS = 30
local LOW_DELTA = 0.25
local DASH_TOP_INSET = 9
local LED_W = 14


function ZedHUDMaskHUD:onRightMouseUp(x, y)
	local playerObj = getSpecificPlayer(0)
	if not playerObj or not self.mask then return true end
	local isTank = self.cfg and self.cfg.isTank or false
	local hasFilter = ZedHUDMask.hasDrainable(self.mask, self.cfg)
	local hasSpare = ZedHUDMask.hasSpareFilterPublic(playerObj)
	local replaceKey = isTank and "ContextMenu_MFI_ReplaceTank" or "ContextMenu_MFI_ReplaceFilter"
	local attachKey = isTank and "ContextMenu_MFI_AttachTank" or "ContextMenu_MFI_AttachFilter"

	local ctx = ISContextMenu.get(0, getMouseX(), getMouseY())
	local offLabel = isTank and (getText("ContextMenu_Unequip") .. " " .. self.mask:getDisplayName())
		or getText("ContextMenu_MFI_TakeOffMask")
	ctx:addOption(offLabel, playerObj, ZedHUDMask.unequipMask)
	if isTank then
		local on = self.mask:isActivated()
		local opt = ctx:addOption(getText(on and "ContextMenu_Turn_Off" or "ContextMenu_Turn_On"),
			playerObj, ZedHUDMask.toggleActivated)
		if not on and not ZedHUDMask.canTurnOn(self.mask) then
			opt.notAvailable = true
			opt.toolTip = ISInventoryPaneContextMenu.addToolTip()
			opt.toolTip.description = getText(self.mask:hasTank() and "Tooltip_IsEmpty" or "Tooltip_NoTank")
		end
	end
	if hasSpare then
		local label = getText(hasFilter and replaceKey or attachKey)
		ctx:addOption(label, playerObj, ZedHUDMask.performSwap)
	elseif not hasFilter then
		local opt = ctx:addOption(getText(attachKey), playerObj, nil)
		opt.notAvailable = true
	end
	return true
end


function ZedHUDMaskHUD:isOverLed(x, y)
	local r = self.ledRect
	return r ~= nil and x >= r.x and x < r.x + r.w and y >= r.y and y < r.y + r.h
end

function ZedHUDMaskHUD:onMouseDown(x, y)
	if self:isOverLed(x, y) then
		self._ledDown = true
		return true
	end
	self._ledDown = false
	return ISPanel.onMouseDown(self, x, y)
end

function ZedHUDMaskHUD:onMouseUpOutside(x, y)
	self._ledDown = false
	return ISPanel.onMouseUpOutside(self, x, y)
end


function ZedHUDMaskHUD:new()
	local o = ISPanel:new(0, 0, DEFAULT_W, MASK_H + PAD * 2)
	o.panelW = DEFAULT_W
	setmetatable(o, self)
	self.__index = self
	o.backgroundColor = { r = 0, g = 0, b = 0, a = 0 }
	o.borderColor = { r = 0, g = 0, b = 0, a = 0 }
	o.moveWithMouse = true
	o._posRestored = false
	o.refreshCounter = REFRESH_TICKS
	o.mask = nil
	o.cfg = nil
	o.spareList = {}
	o.spares = 0
	o.fadeAlpha = 1
	o.drainStrength = 0
	o.ledRect = nil
	o._ledDown = false
	return o
end


local _drawRect = ISPanel.drawRect
local _drawRectBorder = ISPanel.drawRectBorder
local _drawTextureScaledAspect = ISPanel.drawTextureScaledAspect

function ZedHUDMaskHUD:drawRect(x, y, w, h, a, r, g, b)
	_drawRect(self, x, y, w, h, a * self.fadeAlpha, r, g, b)
end

function ZedHUDMaskHUD:drawRectBorder(x, y, w, h, a, r, g, b)
	_drawRectBorder(self, x, y, w, h, a * self.fadeAlpha, r, g, b)
end

function ZedHUDMaskHUD:drawTextureScaledAspect(tex, x, y, w, h, a, r, g, b)
	_drawTextureScaledAspect(self, tex, x, y, w, h, a * self.fadeAlpha, r, g, b)
end

function ZedHUDMaskHUD:onMouseUp(x, y)
	if self._ledDown then
		self._ledDown = false
		if self:isOverLed(x, y) then
			local playerObj = getSpecificPlayer(0)
			if playerObj then ZedHUDMask.toggleActivated(playerObj) end
		end
		return true
	end
	ISPanel.onMouseUp(self, x, y)
	if ZedHUDMaskOptions.getPositionMode() == 2 then
		local p = getSpecificPlayer(0)
		if p then
			local pd = p:getModData()
			pd["ZedHUDMask.posX"] = self:getX()
			pd["ZedHUDMask.posY"] = self:getY()
		end
	end
	return true
end


function ZedHUDMaskHUD:refreshSlow(playerObj)
	self.mask, self.cfg = ZedHUDMask.findWornMask(playerObj)
	if self.mask and self.cfg then
		self.spareList = ZedHUDMask.listSpareFilters(playerObj, self.cfg)
	else
		self.spareList = {}
	end
	self.spares = #self.spareList
	if ZedHUDMaskOptions.isShowDrainBlink() then
		self.drainStrength = ZedHUDMaskHUD.getFilterDrainStrength(playerObj, self.mask, self.cfg)
	else
		self.drainStrength = 0
	end
end

local TOXIC_DRAIN = 0.01

-- ZEDGROUND: 봉쇄 구역 독가스(Blue Zone Style 2)로 필터가 닳는 세기. 원본은 독성 건물·시체 악취만 본다.
-- 서버(BlueZoneServer maskAbsorb)가 깎는 양 = 초당 dps x DamageScale x MaskDrainScale / 100. 독성 건물(TOXIC_DRAIN)을 1 로 본 비율.
-- Blue Zone 이 없거나 라운드가 없거나 구역 안이면 0
local GAS_STYLE = 2
local _gasLogged = nil
local function gasDrainStrength(playerObj)
	local B = BlueZone
	if not (B and B.C and B.opt and B.isPlayerOutside) then return 0 end
	if B.opt("Style") ~= GAS_STYLE or not B.isPlayerOutside(playerObj) then
		if _gasLogged then
			_gasLogged = nil
			ZedHUD.log("mask: gas drain blink off")
		end
		return 0
	end
	local perSec = (B.C.dps or 0) * B.opt("DamageScale") * B.opt("MaskDrainScale") / 100
	if perSec <= 0 then return 0 end
	if _gasLogged ~= B.C.phase then
		_gasLogged = B.C.phase
		ZedHUD.log(string.format("mask: gas drain blink on, phase=%s perSec=%.4f", tostring(B.C.phase), perSec))
	end
	return math.min(1, perSec / TOXIC_DRAIN)
end

function ZedHUDMaskHUD.getFilterDrainStrength(playerObj, mask, cfg)
	if not mask or not cfg or cfg.isTank then return 0 end
	if not mask:hasFilter() or mask:getUsedDelta() <= 0 then return 0 end
	local gas = gasDrainStrength(playerObj)
	if gas > 0 then return gas end
	local building = playerObj:getCurrentBuilding()
	if building and building:isToxic() then return 1 end
	if SandboxVars and SandboxVars.DecayingCorpseHealthImpact == 1 then return 0 end
	local bodyDamage = playerObj:getBodyDamage()
	if not bodyDamage then return 0 end
	local rate = bodyDamage:GetBaseCorpseSickness()
	if rate <= 0 then return 0 end
	local mult = (ZomboidGlobals and ZomboidGlobals.CorpseSicknessFilterDrainMultiplier) or 0.3
	return math.min(1, rate * mult / TOXIC_DRAIN)
end

local BLINK_SLOW_MS = 2000
local BLINK_FAST_MS = 300
local BLINK_LOG_MIN = -2.5
function ZedHUDMaskHUD.blinkAlpha(strength, baseA)
	if strength <= 0 then return baseA end
	local t = (math.log(strength) / math.log(10) - BLINK_LOG_MIN) / -BLINK_LOG_MIN
	t = math.max(0, math.min(1, t))
	local period = BLINK_SLOW_MS - (BLINK_SLOW_MS - BLINK_FAST_MS) * t
	local phase = (getTimestampMs() % period) / period
	return baseA * (0.2 + 0.8 * (0.5 + 0.5 * math.cos(phase * 2 * math.pi)))
end
local blinkAlpha = ZedHUDMaskHUD.blinkAlpha


local function shouldShowMaskFor(mask, cfg)
	if not mask then return false end
	if ZedHUDMaskOptions.getHudMode() == 2 then
		return (not ZedHUDMask.hasDrainable(mask, cfg)) or mask:getUsedDelta() <= LOW_DELTA
	end
	return true
end

local function blockColor(delta)
	if delta > 0.5 then return 0.45, 0.85, 0.45 end
	if delta > LOW_DELTA then return 0.95, 0.8, 0.3 end
	return 0.95, 0.35, 0.35
end

local function pipColor(delta)
	if delta > 0.5 then return 0.92, 0.92, 0.92 end
	if delta > LOW_DELTA then return 0.95, 0.8, 0.3 end
	return 0.95, 0.35, 0.35
end


function ZedHUDMaskHUD:prerender()
end

function ZedHUDMaskHUD:renderMaskSection(rowY)
	local tex = self.mask:getTex()
	if tex then
		self:drawTextureScaledAspect(tex, PAD + math.floor((ICON - MASK_H) / 2), rowY, MASK_H, MASK_H, 1, 1, 1, 1)
	end

	local isTank = self.cfg and self.cfg.isTank or false
	local hasFilter = ZedHUDMask.hasDrainable(self.mask, self.cfg)
	local delta = hasFilter and self.mask:getUsedDelta() or 0
	local filled = math.floor(delta * BLOCKS + 0.000001)
	local r, g, b = blockColor(delta)
	local active = (not isTank) or self.mask:isActivated()
	local fillA = active and 0.9 or 0.35
	local barX = PAD + ICON + PAD
	local barAreaW = self.panelW - barX - PAD
	if isTank then
		barAreaW = barAreaW - (LED_W + GAP * 2)
	end
	local bw = math.floor((barAreaW + GAP) / BLOCKS) - GAP
	for i = 0, BLOCKS - 1 do
		local bx = barX + i * (bw + GAP)
		if i < filled then
			local a = (i == filled - 1) and blinkAlpha(self.drainStrength, fillA) or fillA
			self:drawRect(bx, rowY, bw, BLOCK_H, a, r, g, b)
		else
			self:drawRectBorder(bx, rowY, bw, BLOCK_H, 0.5, 0.45, 0.45, 0.45)
		end
	end
	if hasFilter and filled == 0 and delta > 0 then
		local sliverW = math.max(2, math.floor(bw * delta * BLOCKS + 0.5))
		self:drawRect(barX, rowY, sliverW, BLOCK_H, blinkAlpha(self.drainStrength, fillA), r, g, b)
	end
	if isTank then
		local lx = barX + BLOCKS * (bw + GAP) - GAP + GAP * 2
		self.ledRect = { x = lx, y = rowY, w = LED_W, h = MASK_H }
		local canOn = active or ZedHUDMask.canTurnOn(self.mask)
		local fa = self._ledDown and 0.9 or (canOn and 0.6 or 0.3)
		self:drawRect(lx, rowY, LED_W, MASK_H, 0.6, 0.08, 0.08, 0.08)
		self:drawRectBorder(lx, rowY, LED_W, MASK_H, fa, 0.7, 0.7, 0.7)
		local dot = math.max(4, LED_W - 6)
		local dx = lx + math.floor((LED_W - dot) / 2)
		local dy = rowY + math.floor((MASK_H - dot) / 2)
		if active then
			self:drawRect(dx - 1, dy - 1, dot + 2, dot + 2, 0.35, 0.3, 1.0, 0.3)
			self:drawRect(dx, dy, dot, dot, 0.95, 0.3, 1.0, 0.3)
		else
			self:drawRect(dx, dy, dot, dot, 0.9, 0.22, 0.3, 0.22)
		end
	else
		self.ledRect = nil
	end
	if not hasFilter then
		local fullW = BLOCKS * (bw + GAP) - GAP
		self:drawRectBorder(barX - 2, rowY - 2, fullW + 4, BLOCK_H + 4, 0.8, 0.9, 0.3, 0.3)
	end

	local pipY = rowY + BLOCK_H + GAP
	local pipCount = math.min(self.spares, BLOCKS)
	for i = 0, BLOCKS - 1 do
		local bx = barX + i * (bw + GAP)
		self:drawRectBorder(bx, pipY, bw, PIP_H, 0.5, 0.45, 0.45, 0.45)
		if i < pipCount then
			local d = math.max(0, math.min(1, self.spareList[i + 1] or 0))
			local pr, pg, pb = pipColor(d)
			local fw = math.max(2, math.floor(bw * d + 0.5))
			self:drawRect(bx, pipY, fw, PIP_H, 0.85, pr, pg, pb)
		end
	end
end

function ZedHUDMaskHUD:render()
	local playerObj = getSpecificPlayer(0)
	if not playerObj or playerObj:isDead() then
		return
	end

	self.refreshCounter = self.refreshCounter + 1
	if self.refreshCounter >= REFRESH_TICKS then
		self.refreshCounter = 0
		self:refreshSlow(playerObj)
	end
	if not self.mask then return end

	local h = MASK_H + PAD * 2

	local freeMode = ZedHUDMaskOptions.getPositionMode() == 2
	local hotbar = getPlayerHotbar and getPlayerHotbar(0) or nil
	if freeMode then
		self.fadeAlpha = 1
		self.panelW = DEFAULT_W
		self:setWidth(self.panelW)
		self:setHeight(h)
		if not self._posRestored then
			local pd = playerObj:getModData()
			local sx = pd["ZedHUDMask.posX"]
			local sy = pd["ZedHUDMask.posY"]
			local screenW, screenH = getCore():getScreenWidth(), getCore():getScreenHeight()
			self:setX(type(sx) == "number" and sx or math.floor((screenW - self.panelW) / 2))
			self:setY(type(sy) == "number" and sy or math.floor(screenH * 0.75))
			self._posRestored = true
		end
	else
		self._posRestored = false
		local dash = getPlayerVehicleDashboard and getPlayerVehicleDashboard(0) or nil
		local dashMode = (dash ~= nil and dash.vehicle ~= nil and dash:getIsVisible()) and true or false
		local hotbarReady = (hotbar ~= nil and hotbar:getIsVisible()) and true or false
		self.fadeAlpha = (dashMode or hotbarReady) and 1 or 0

		if dashMode then
			local sw = (hotbar and hotbar.slotWidth) or 60
			local pad = (hotbar and hotbar.slotPad) or 4
			self.panelW = math.floor(sw * 3 + pad * 2)
			self:setWidth(self.panelW)
			self:setHeight(h)
			self:setX(math.floor(dash:getX() + (dash:getWidth() - self.panelW) / 2))
			self:setY(math.floor(dash:getY() + DASH_TOP_INSET - h - PAD))
		elseif hotbarReady then
			local sw = hotbar.slotWidth or 60
			local pad = hotbar.slotPad or 4
			self.panelW = math.floor(sw * 3 + pad * 2)
			self:setWidth(self.panelW)
			self:setHeight(h)
			self:setX(math.floor(hotbar:getX() + (hotbar:getWidth() - self.panelW) / 2))
			self:setY(math.floor(hotbar:getY() - h - PAD))
		else
			self:setWidth(self.panelW)
			self:setHeight(h)
		end
	end

	self:drawRect(0, 0, self.panelW, h, 0.5, 0, 0, 0)
	self:drawRectBorder(0, 0, self.panelW, h, 0.4, 0.6, 0.6, 0.6)
	self:renderMaskSection(PAD)
end


local hudInstance = nil

local function shouldShowHUD(playerObj)
	if ZedHUDMask.disabled then return false end
	if not playerObj or playerObj:isDead() then return false end
	local mask, cfg = ZedHUDMask.findWornMask(playerObj)
	return shouldShowMaskFor(mask, cfg)
end

local function createHUD()
	if hudInstance then return end
	hudInstance = ZedHUDMaskHUD:new()
	hudInstance:initialise()
	hudInstance:instantiate()
	hudInstance:setVisible(true)
	hudInstance:addToUIManager()
	ZedHUD.log("mask: HUD shown")
end

local function destroyHUD()
	if not hudInstance then return end
	hudInstance:removeFromUIManager()
	hudInstance = nil
	ZedHUD.log("mask: HUD hidden")
end

local missCount = 0
local MISS_LIMIT = 2

local function checkVisibility()
	if shouldShowHUD(getSpecificPlayer(0)) then
		missCount = 0
		if not hudInstance then createHUD() end
	else
		missCount = missCount + 1
		if missCount >= MISS_LIMIT and hudInstance then destroyHUD() end
	end
end

local watchCounter = 0
local function tickWatcher()
	watchCounter = watchCounter + 1
	if watchCounter >= REFRESH_TICKS then
		watchCounter = 0
		checkVisibility()
	end
end

local function onClothingUpdated(playerObj)
	if playerObj == getSpecificPlayer(0) then
		checkVisibility()
	end
end

Events.OnTick.Add(tickWatcher)
Events.OnClothingUpdated.Add(onClothingUpdated)
Events.OnGameStart.Add(checkVisibility)
