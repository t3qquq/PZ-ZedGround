--[[ ZEDGROUND HUD : 모드 옵션 (클라별, PZAPI.ModOptions). 게임 안 경로: 옵션 -> 모드 -> [ZEDGROUND] HUD
  방독면 필터 표시 옵션: 원본 Mask Filter Indicator by movinow (id=3832759095) 의 옵션 그대로
  지도 내 위치 표시 크기: 원본 Compass Direction Indicator by qwerto (id=3787835610) 의 옵션 그대로
  두 제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 재배포하지 말 것.
]]

ZedHUDMaskOptions = ZedHUDMaskOptions or {}
ZedHUDMapMarker = ZedHUDMapMarker or {}

local options = PZAPI.ModOptions:create("t3ZedHUD", "[ZEDGROUND] HUD")

-- 지도 내 위치 표시 크기 (원본 Compass Direction Indicator 의 "size" 슬라이더, 100 = 9px 점).
-- 하한 50 은 원본 그대로: 엔진이 그리는 6x6 빨간 사각형을 점이 덮어야 한다 (ZedHUDMapMarker.lua MIN_COVER_RADIUS).
-- 주의: 이 파일의 글자에는 "%" 를 넣지 말 것 - getText 가 String.format 을 한 번 더 돌린다 (42.20.1~)
options:addSlider("mapMarkerSize", "UI_ZedHUD_MapMarkerSize", 50, 250, 25, 100)
ZedHUDMapMarker.options = options


ZedHUDMaskOptions.PositionMode = options:addComboBox(
	"PositionMode",
	getText("UI_MFI_Opt_Position"),
	getText("UI_MFI_Opt_Position_TT")
)
ZedHUDMaskOptions.PositionMode:addItem(getText("UI_MFI_Opt_Position_Hotbar"), true)
ZedHUDMaskOptions.PositionMode:addItem(getText("UI_MFI_Opt_Position_Free"))

ZedHUDMaskOptions.HudMode = options:addComboBox(
	"HudMode",
	getText("UI_MFI_Opt_MaskHud"),
	getText("UI_MFI_Opt_MaskHud_TT")
)
ZedHUDMaskOptions.HudMode:addItem(getText("UI_MFI_Opt_MaskHud_Always"), true)
ZedHUDMaskOptions.HudMode:addItem(getText("UI_MFI_Opt_MaskHud_Alert"))

ZedHUDMaskOptions.KeepMaskOn = options:addTickBox(
	"KeepMaskOn",
	getText("UI_MFI_Opt_KeepMaskOn"),
	false,
	getText("UI_MFI_Opt_KeepMaskOn_TT")
)

ZedHUDMaskOptions.NotifyEmpty = options:addTickBox(
	"NotifyEmpty",
	getText("UI_MFI_Opt_NotifyEmpty"),
	true,
	getText("UI_MFI_Opt_NotifyEmpty_TT")
)

ZedHUDMaskOptions.ShowDrainBlink = options:addTickBox(
	"ShowDrainBlink",
	getText("UI_MFI_Opt_ShowDrainBlink"),
	true,
	getText("UI_MFI_Opt_ShowDrainBlink_TT")
)

ZedHUDMaskOptions.HudMode.onChange = function(self, box)
	if box ~= nil and box.selected ~= nil then
		self.selected = box.selected
	end
end
ZedHUDMaskOptions.KeepMaskOn.onChange = function(self, selected)
	self.value = selected
end
ZedHUDMaskOptions.NotifyEmpty.onChange = function(self, selected)
	self.value = selected
end
ZedHUDMaskOptions.ShowDrainBlink.onChange = function(self, selected)
	self.value = selected
end
ZedHUDMaskOptions.PositionMode.onChange = function(self, box)
	if box ~= nil and box.selected ~= nil then
		self.selected = box.selected
	end
end


function ZedHUDMaskOptions.getHudMode()
	local o = ZedHUDMaskOptions.HudMode
	return (o and o.selected) or 1
end

function ZedHUDMaskOptions.isKeepMaskOn()
	local o = ZedHUDMaskOptions.KeepMaskOn
	if o == nil or o.value == nil then return false end
	return o.value == true
end

function ZedHUDMaskOptions.isShowDrainBlink()
	local o = ZedHUDMaskOptions.ShowDrainBlink
	if o == nil or o.value == nil then return true end
	return o.value == true
end

function ZedHUDMaskOptions.isNotifyEmpty()
	local o = ZedHUDMaskOptions.NotifyEmpty
	if o == nil or o.value == nil then return true end
	return o.value == true
end

function ZedHUDMaskOptions.getPositionMode()
	local o = ZedHUDMaskOptions.PositionMode
	return (o and o.selected) or 1
end

local function applyComboTooltips()
	local maps = {
		[ZedHUDMaskOptions.PositionMode] = {
			defaultTooltip = getText("UI_MFI_Opt_Position_TT"),
		},
		[ZedHUDMaskOptions.HudMode] = {
			defaultTooltip = getText("UI_MFI_Opt_MaskHud_TT"),
		},
	}
	for opt, map in pairs(maps) do
		if opt and opt.element and opt.element.setToolTipMap then
			opt.element:setToolTipMap(map)
		end
	end
end

local function patchModOptionsTooltips()
	if not MainOptions or not MainOptions.addModOptionsPanel then return end
	local orig = MainOptions.addModOptionsPanel
	MainOptions.addModOptionsPanel = function(self, ...)
		orig(self, ...)
		applyComboTooltips()
	end
end
Events.OnGameBoot.Add(patchModOptionsTooltips)
