--[[ ZEDGROUND HUD : 방독면을 쓴 채 필터를 갈 때의 벗기·쓰기 동작
  원본: Mask Filter Indicator by movinow
  https://steamcommunity.com/sharedfiles/filedetails/?id=3832759095
  제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 이 파일을 다른 곳에 재배포하지 말 것.
  원본과 다른 점: 전역·파일 이름(MaskFilterIndicator* -> ZedHUDMask*), 모드 옵션은 ZedHUDOptions.lua 로 합침,
  봉쇄 구역 독가스로 필터가 닳을 때도 막대가 깜빡인다, 로그 추가. 그 밖의 동작·수치는 원본 그대로.
]]

require "TimedActions/ISBaseTimedAction"
require "TimedActions/ISWearClothing"

ZedHUDMaskMotion = ISBaseTimedAction:derive("ZedHUDMaskMotion")

function ZedHUDMaskMotion:isValid()
	return self.item ~= nil and self.character:isEquippedClothing(self.item)
end

function ZedHUDMaskMotion:update()
	self.item:setJobDelta(self:getJobDelta())
end

function ZedHUDMaskMotion:start()
	self.item:setJobType(self.jobLabel)
	self.item:setJobDelta(0.0)
	self:setActionAnim("WearClothing")
	self:setAnimVariable("WearClothingLocation", WearClothingAnimations[self.item:getBodyLocation()] or "")
	self.character:reportEvent("EventWearClothing")
	self.sound = self.character:playSound("RummageInInventory")
end

function ZedHUDMaskMotion:stopSound()
	if self.sound and self.character:getEmitter():isPlaying(self.sound) then
		self.character:getEmitter():stopSound(self.sound)
	end
end

function ZedHUDMaskMotion:stop()
	self.item:setJobDelta(0.0)
	self:stopSound()
	ISBaseTimedAction.stop(self)
end

function ZedHUDMaskMotion:perform()
	self.item:setJobDelta(0.0)
	self:stopSound()
	ISBaseTimedAction.perform(self)
end

function ZedHUDMaskMotion:new(character, item, jobLabel)
	local o = ISBaseTimedAction.new(self, character)
	o.item = item
	o.jobLabel = jobLabel
	o.stopOnWalk = ISWearClothing.isStopOnWalk(item)
	o.stopOnRun = true
	o.maxTime = 50
	if character:isTimedActionInstant() then
		o.maxTime = 1
	end
	return o
end
