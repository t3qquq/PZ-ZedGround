--[[ ZEDGROUND HUD : 지도·미니맵 내 위치 표시 (빨간 점 + 보는 방향 부채꼴)
     원본: Compass Direction Indicator by qwerto
     https://steamcommunity.com/sharedfiles/filedetails/?id=3787835610
     제작자 허락을 받아 ZEDGROUND 서버용으로 고쳐 내장했다 (11-22). 이 파일을 다른 곳에 재배포하지 말 것.
     원본과 다른 점:
       - 전역·파일·텍스처 이름 (CompassDirectionIndicator -> ZedHUDMapMarker, CDI_*.png -> ZedHUD_map_*.png)
       - 방향 소스: 캐릭터 정면 대신 상단 나침반과 같은 ZedHUD.headingOf (Viewpoint 1·3인칭이면 카메라)
       - 나침반 소지 조건: 샌드박스 ZedHUD.MapConeRequireCompass (기본 꺼짐 = 항상 표시)
       - 크기 옵션은 ZedHUDOptions.lua 의 mapMarkerSize, 로그 추가
     아래 영문 주석은 원본 그대로다. ]]

--[[ Compass Direction Indicator -- the marker itself, and the hooks that put it on
     each of the game's maps.

     The vanilla player marker is a 6x6 pure-red SQUARE with no heading, drawn in Java
     from UIWorldMap.renderPlayer and again inline in UIWorldMap.render. There is no Lua
     seam between those two draws, so this mod does not replace that square, it COVERS it
     -- see CDI.MIN_COVER_RADIUS.

     Drawing on top is possible because UIWorldMap.render() ends by calling
     UIElement.render(), which is what dispatches the Lua-side render -- so a Lua
     render hook on a map panel runs AFTER the whole Java map, players included.

     THIS MOD DRAWS EXACTLY ONE MARK, YOURS, and that is the whole of the multiplayer
     design. By the time that dispatch runs the engine has already drawn everyone else --
     renderLocalPlayers for the local roster (splitscreen), renderRemotePlayers for
     multiplayer remotes, the latter filtered per connection by the server and captioned
     with a name plate. The shape cue carries the difference: you are a circle with a
     heading cone, everyone else is the engine's red square. ]]

require "ISUI/Maps/ISWorldMap"
require "ISUI/Maps/ISMiniMap"
require "ZedHUDConfig"

ZedHUDMapMarker = ZedHUDMapMarker or {}
local CDI = ZedHUDMapMarker

-- Marker color, matching PZ_Map's dashboard marker (#e8443a) rather than the engine's
-- pure red, so the two read as the same indicator.
CDI.RED = { r = 232 / 255, g = 68 / 255, b = 58 / 255 }

-- Dot radius in SCREEN pixels at the 100% size setting. Everything else -- ring width,
-- cone reach -- derives from it, so the whole marker scales as one shape.
CDI.BASE_RADIUS = 9

-- The engine draws its 6x6 square at PZMath.floor(uiX) - 3 -- on the WHOLE pixel, not on
-- the raw projected position. The marker is centered on that same floored pixel
-- (CDI.markUI), so concentric with the square its corners sit exactly 3*sqrt(2) = 4.25px
-- out and the dot has only to reach that far. Centered on the raw position instead, the
-- square could sit a pixel off inside the dot and the figure would be sqrt(4^2+4^2) =
-- 5.66 -- more than the smallest dot the slider offers.
CDI.MIN_COVER_RADIUS = 4.25

-- White outline width, as a fraction of the dot's radius, so the outline grows with the
-- marker rather than thinning to a hairline at the top of the slider. RING_MIN is a floor
-- in WHOLE PIXELS and deliberately not scaled: below a pixel there is no outline, only a
-- tint on the dot's edge.
CDI.RING_MIN   = 1
CDI.RING_RATIO = 0.18
CDI.CONE_REACH = 7      -- cone length in dot-radii
CDI.CONE_ALPHA = 0.7    -- at the apex; the texture's own gradient fades it to nothing

-- Probe distance in tiles for turning a world-space heading into a screen-space one
-- (CDI.projectHeading). Long enough that the projected delta is comfortably longer than a
-- pixel at any zoom, which is why the unit vector is not projected directly.
CDI.HEADING_PROBE = 16

-- Heading easing. The engine's forward direction SNAPS -- a survivor turns their head
-- instantly -- and with the minimap permanently on screen that reads as a twitch rather
-- than as looking around, so the cone is eased toward the live heading rather than pinned
-- to it.
--
-- EASE is the fraction of the remaining turn taken per 60fps frame, rescaled to the real
-- frame time by the exponent below so the glide is the same speed at any framerate. 0.2
-- covers 90 percent of a turn in about 170ms, which reads as a swing rather than as lag.
CDI.EASE = 0.2
CDI.EASE_FRAME_MS = 1000 / 60
-- Longer than this since the last ease and the stored heading is snapped to rather than
-- eased from: the marker was not being drawn -- map closed, minimap hidden, a new
-- character -- and easing from a heading that old would sweep the cone across the screen
-- the moment the map opens.
CDI.EASE_STALE_MS = 1000

local TEX_DISC = "media/textures/ZedHUD_map_disc.png"
local TEX_CONE = "media/textures/ZedHUD_map_cone.png"

-- Textures are fetched on first draw, not at file load: the texture manager is not
-- necessarily populated while mod Lua is still being read.
function CDI.textures()
    if CDI._disc == nil then
        CDI._disc = getTexture(TEX_DISC) or false
        CDI._cone = getTexture(TEX_CONE) or false
        ZedHUD.log("map marker: textures disc=" .. tostring(CDI._disc ~= false) .. " cone=" .. tostring(CDI._cone ~= false))
    end
    return CDI._disc, CDI._cone
end

-- The size option, read live every frame: a cached copy silently keeps the old value when
-- the player changes it mid-session. Falls back to the shipped default, so the marker
-- still draws at its normal size if the options file failed to load.
local function optValue(id, default)
    local opts = CDI.options
    if opts == nil then return default end
    local opt = opts:getOption(id)
    if opt == nil then return default end
    local v = opt:getValue()
    if v == nil then return default end
    return v
end

--[[ The size option as a multiplier, floored so the marker always covers the engine's
     square (CDI.MIN_COVER_RADIUS).

     The slider's own minimum of 50 is a UI floor, not a value floor: PZAPI's ini loader
     assigns straight into the option with no range check against the declaration, so a
     hand-edited ModOptions.ini arrives here with any number at all.

     The floor is DERIVED from the two constants rather than written out, so lowering either
     moves it -- and it lands under the slider's own minimum, which is what keeps it free:
     the slider can never reach it, so it can never become a dead zone at the bottom of the
     control. ]]
function CDI.sizeScale()
    local scale = optValue("mapMarkerSize", 100) / 100
    local floor = CDI.MIN_COVER_RADIUS / CDI.BASE_RADIUS
    if scale < floor then return floor end
    return scale
end

--[[ The marker's two radii, in WHOLE screen pixels.

     They have to be whole. ISUIElement:drawTextureScaled ends in
     UIElement.DrawTextureScaledCol, which TRUNCATES x, y, w and h to int, so a fractional
     radius loses a different number of pixels off each of the four edges and the white
     outline draws wider on one side than the other. Rounded onto the whole pixel the mark
     is already centered on (CDI.markUI), the truncation is a no-op -- and a fractional
     panel origin cannot reintroduce it, since both quads take that same offset.

     The dot rounds UP at the bottom of the range: CDI.sizeScale's clamp lands exactly on
     CDI.MIN_COVER_RADIUS = 4.25, and 4px would uncover the square's corners. ]]
function CDI.markerPx(scale)
    local r = math.floor(CDI.BASE_RADIUS * scale + 0.5)
    local minRadius = math.ceil(CDI.MIN_COVER_RADIUS)
    if r < minRadius then r = minRadius end

    local ring = math.floor(r * CDI.RING_RATIO + 0.5)
    if ring < CDI.RING_MIN then ring = CDI.RING_MIN end

    return r, ring
end

--[[ Does this world require a compass for the cone? The per-world rule, declared in
     42/media/sandbox-options.txt as Compass_Direction_Indicator.RequireCompass.

     READ LIVE, NEVER CACHED. An admin can change a sandbox option mid-session (Admin Panel ->
     Sandbox Options -> Apply) and the game copies the new value into every client's
     SandboxVars, so a cached answer would keep the old rule until the next world load. This
     runs once per map panel per frame, and a plain table lookup is cheap enough for that and
     cannot throw.

     Anything missing answers TRUE, the compass gate: a world made before the option existed,
     or a SandboxVars not populated yet, never shows a heading the world did not ask for. ]]
function CDI.requireCompass()
    -- ZEDGROUND: 샌드박스 ZedHUD.MapConeRequireCompass. 등록된 옵션은 로드 후 항상 값이 있다 (fallback 불필요)
    return SandboxVars.ZedHUD.MapConeRequireCompass
end

-- Latched on the first throw, because a pcall stops the crash and not the log line, and
-- the check below runs once per map panel per frame: without this, one renamed engine
-- method would write a stack trace at the player's framerate for the rest of the session.
CDI._compassBroken = false

-- True while the character carries a directional compass anywhere in their inventory,
-- bags included. Uses the engine's own ItemTag.COMPASS (the `base:compass` tag on
-- CompassDirectional), which correctly excludes CompassGeometry -- the drafting compass,
-- which is tagged base:hasmetal and nothing else. pcall-guarded so a missing method or
-- enum in a future build degrades to "no compass" rather than throwing.
function CDI.hasCompass(character)
    if CDI._compassBroken then return false end
    local ok, result = pcall(function()
        local inv = character:getInventory()
        if not (inv and ItemTag and ItemTag.COMPASS) then return false end
        return inv:containsTagRecurse(ItemTag.COMPASS) == true
    end)
    if not ok then
        CDI._compassBroken = true
        ZedHUD.log("map marker: compass check FAILED err=" .. tostring(result) .. " -> treated as no compass")
    end
    return ok and result == true
end

--[[ The eased heading, one entry per local player: { dx, dy, t }.

     Keyed by player number and advanced by ELAPSED TIME rather than per call, which is what
     lets the world map and the minimap both draw the same character in one frame: whichever
     runs first advances the ease, the second sees dt = 0 and reads the same vector back. Two
     panels can never turn the cone twice as fast as one.

     Eased in WORLD space, before the projection. Smoothing the projected vector instead
     would make the cone swing every time the map is panned or the perspective toggled --
     the heading would not have changed, only where it points on screen. ]]
CDI.headings = {}

-- The heading to draw: the survivor's live forward direction, eased. Returns a world-space
-- unit vector, or nil if they have never faced anywhere (getForwardDirection is zero until
-- the first move).
--
-- Eased as a VECTOR and renormalized, not as an angle, so the cone always takes the short
-- way round with no wrap-around case to get wrong.
function CDI.easedHeading(character)
    -- ZEDGROUND: 상단 나침반과 같은 방향 소스. 방위(0 = 북, 시계방향) -> 월드 단위 벡터 (x = 동, y = 남)
    local dx, dy
    local deg, src = ZedHUD.headingOf(character)
    if src == "camera" then
        local rad = math.rad(deg)
        dx, dy = math.sin(rad), -math.cos(rad)
    else
        dx = character:getForwardDirectionX()
        dy = character:getForwardDirectionY()
    end
    if dx == nil or dy == nil then return nil end

    local key = character:getPlayerNum() or 0
    local held = CDI.headings[key]

    -- Facing nowhere: hold the last heading rather than easing toward zero, which would
    -- shrink the vector without ever changing where it points.
    if dx == 0 and dy == 0 then
        if held then return held.dx, held.dy end
        return nil
    end

    local now = getTimestampMs()
    if held == nil or (now - held.t) > CDI.EASE_STALE_MS then
        CDI.headings[key] = { dx = dx, dy = dy, t = now }
        return dx, dy
    end

    local dt = now - held.t
    -- Explicit rather than load-bearing: at dt = 0 the exponent below already makes k zero.
    -- The guard states the property outright -- the ease advances with the clock, not with
    -- the call.
    if dt > 0 then
        held.t = now
        local k = 1 - (1 - CDI.EASE) ^ (dt / CDI.EASE_FRAME_MS)
        held.dx = held.dx + (dx - held.dx) * k
        held.dy = held.dy + (dy - held.dy) * k
        local mag = math.sqrt(held.dx * held.dx + held.dy * held.dy)
        if mag > 0.001 then
            held.dx, held.dy = held.dx / mag, held.dy / mag
        else
            held.dx, held.dy = dx, dy   -- exact reversal: the ease passed through zero
        end
    end
    return held.dx, held.dy
end

-- A world-space heading as a UNIT VECTOR IN SCREEN SPACE.
--
-- The direction is projected rather than computed: the map has an isometric mode, and
-- rather than reimplement that shear this asks the engine where a point some way ahead of
-- the player lands and takes the difference -- correct under whatever projection the map
-- happens to be using.
function CDI.projectHeading(mapAPI, wx, wy, ux, uy, dx, dy)
    local px = wx + dx * CDI.HEADING_PROBE
    local py = wy + dy * CDI.HEADING_PROBE
    local hx = mapAPI:worldToUIX(px, py) - ux
    local hy = mapAPI:worldToUIY(px, py) - uy
    local len = math.sqrt(hx * hx + hy * hy)
    if len < 0.0001 then return nil end
    return hx / len, hy / len
end

--[[ Draw the cone: a square texture quad of side 2*reach, centered on the player and
     rotated so the texture's +x axis lands along the heading. The apex is at the center of
     the texture -- the one point a rotation about the center leaves alone -- so it stays
     pinned to the player however the quad turns.

     The corner math is the only place rotation appears. With the heading unit vector
     (hx, hy) in screen space (y DOWN), the matrix taking texture axes to screen axes has
     columns (hx, hy) and (-hy, hx) -- texture +x lands along the heading, texture +y
     along its perpendicular -- and each corner is the center plus that matrix applied to
     the corner's texture-space offset.

     drawTextureAllPoint is the raw 12-double UIElement.DrawTexture -- the only draw that
     does rotation AND scale AND alpha at once -- and it takes ABSOLUTE screen coordinates
     with no element offset applied, unlike every wrapped draw. Hence the ax/ay below. ]]
function CDI.drawCone(ui, cone, cx, cy, reach, hx, hy)
    local ax = ui:getAbsoluteX() + cx
    local ay = ui:getAbsoluteY() + cy
    local h = reach

    local tlx, tly = ax + h * (hy - hx), ay - h * (hy + hx)
    local trx, try = ax + h * (hx + hy), ay + h * (hy - hx)
    local brx, bry = ax + h * (hx - hy), ay + h * (hy + hx)
    local blx, bly = ax - h * (hx + hy), ay + h * (hx - hy)

    ui:drawTextureAllPoint(cone, tlx, tly, trx, try, brx, bry, blx, bly,
        CDI.RED.r, CDI.RED.g, CDI.RED.b, CDI.CONE_ALPHA)
end

--[[ Where a character's mark goes, in world coordinates.

     Vanilla puts the mark on the VEHICLE when the character is in one, so the two marks
     can never disagree about where you are -- renderLocalPlayers makes the same
     substitution before drawing the square this dot covers. ]]
function CDI.markWorldPos(character)
    local vehicle = character:getVehicle()
    if vehicle ~= nil then return vehicle:getX(), vehicle:getY() end
    return character:getX(), character:getY()
end

-- World position -> the panel pixel the mark is drawn on. FLOORED to land on the same
-- whole pixel as the engine's square (CDI.MIN_COVER_RADIUS), which also means the mark
-- snaps exactly as that square does, so the square cannot slide around inside the dot
-- while the map pans. Returns the raw position too -- CDI.projectHeading subtracts it, and
-- there the sub-pixel truth is the more useful one.
function CDI.markUI(mapAPI, wx, wy)
    local ux = mapAPI:worldToUIX(wx, wy)
    local uy = mapAPI:worldToUIY(wx, wy)
    return math.floor(ux), math.floor(uy), ux, uy
end

--[[ Put this panel's one mark on it: your dot, and the compass-gated heading cone.

     `ui` is the Lua map element (any of the maps the game builds around a UIWorldMap),
     `mapAPI` its map API, `character` the survivor this panel belongs to -- the panel's
     own, never getPlayer(), which in splitscreen is whoever the engine is drawing this
     frame. Each panel gets this call for its OWN survivor.

     Vanilla's "Players" tick box stays the master switch: turning it off hides the
     engine's squares, and this reads the same flag so the dot goes with them. ]]
function CDI.drawOn(ui, mapAPI, character)
    if ui == nil or mapAPI == nil or character == nil then return end
    if not mapAPI:getBoolean("Players") then return end

    local disc, cone = CDI.textures()
    if not disc then return end

    local r, ring = CDI.markerPx(CDI.sizeScale())

    --[[ Clip to the panel. The engine clears its own stencil before the Lua render runs,
         so without this the minimap's marks spill over the window frame and onto the HUD
         whenever the map is scrolled away from the player.

         Set on FIRST DRAW, because the cull that decides whether anything is drawn at
         all lives inside CDI.drawSelf, past this point: a panel whose player is off the
         edge -- or who is dead -- must touch the stencil no more than it touches the
         screen. ]]
    local clipped = false
    local function clip()
        if clipped then return end
        clipped = true
        ui:setStencilRect(0, 0, ui:getWidth(), ui:getHeight())
    end

    if not character:isDead() then
        CDI.drawSelf(ui, mapAPI, character, disc, cone, r, ring, clip)
    end

    if clipped then ui:clearStencilRect() end
end

-- Your own mark: the compass-gated heading cone, then the dot and its outline on top.
function CDI.drawSelf(ui, mapAPI, character, disc, cone, r, ring, clip)
    local wx, wy = CDI.markWorldPos(character)
    local ux, uy, rawX, rawY = CDI.markUI(mapAPI, wx, wy)

    local reach = r * CDI.CONE_REACH
    local margin = reach + r + 2
    if ux < -margin or uy < -margin
            or ux > ui:getWidth() + margin or uy > ui:getHeight() + margin then
        return
    end

    clip()

    -- The cone is gated on actually carrying a compass, which is the mod's premise: it is
    -- the nineties, and a position you can read off landmarks is not the same as knowing
    -- which way you are facing. A world can lift that gate (CDI.requireCompass), and that
    -- cheap lookup is asked first so a world that has lifted it walks no inventory. The dot
    -- is unconditional either way.
    if cone and (not CDI.requireCompass() or CDI.hasCompass(character)) then
        local dx, dy = CDI.easedHeading(character)
        if dx ~= nil then
            local hx, hy = CDI.projectHeading(mapAPI, wx, wy, rawX, rawY, dx, dy)
            if hx ~= nil then
                CDI.drawCone(ui, cone, ux, uy, reach, hx, hy)
            end
        end
    end

    -- Dot and outline from the same disc texture, drawn twice: white and large for the
    -- ring, then the marker color inset by the ring's width. One texture, so the ring can
    -- never end up misaligned with the dot it outlines. Both radii are whole pixels
    -- (CDI.markerPx), which is what keeps the outline even on all four sides.
    local outer = r + ring
    ui:drawTextureScaled(disc, ux - outer, uy - outer, outer * 2, outer * 2, 1, 1, 1, 1)
    ui:drawTextureScaled(disc, ux - r, uy - r, r * 2, r * 2, 1,
        CDI.RED.r, CDI.RED.g, CDI.RED.b)
end

-- --- hooks -------------------------------------------------------------------
--
-- Installed from OnGameStart, not at file load, so these wrappers go on LAST: every mod's
-- Lua is loaded before that event fires. A map mod that ASSIGNS ISWorldMap.render instead
-- of saving and calling it discards whatever is already installed, with no error, and load
-- order between two mods is the subscriber's to arrange.

function CDI.installHooks()
    if CDI._hooked then return end  -- one install only: wrapping twice draws the mark twice
    CDI._hooked = true
    -- 원본 모드가 같이 켜져 있으면 표시가 두 번 그려진다 -> 이쪽은 걸지 않는다
    if CompassDirectionIndicator ~= nil then
        ZedHUD.log("map marker: original Compass Direction Indicator mod is active -> built-in copy DISABLED")
        return
    end

    -- The full-screen world map (M). Its render already draws over the Java map -- the
    -- joypad crosshair and prompts go through here -- so appending is enough.
    local worldMapRender = ISWorldMap.render
    function ISWorldMap:render()
        worldMapRender(self)
        CDI.drawOn(self, self.mapAPI, self.character or getSpecificPlayer(self.playerNum or 0))
    end

    -- The minimap. ISMiniMapInner has no render of its own yet; the save-and-call is for
    -- the build where it grows one.
    local miniMapRender = ISMiniMapInner.render
    function ISMiniMapInner:render()
        if miniMapRender then miniMapRender(self) end
        CDI.drawOn(self, self.mapAPI, getSpecificPlayer(self.playerNum or 0))
    end
    ZedHUD.log("map marker: hooks installed (world map + minimap), requireCompass=" .. tostring(CDI.requireCompass()))
end

Events.OnGameStart.Add(CDI.installHooks)
