// Shapes copied from what YouTube's web client actually receives (trimmed to the parts that matter).
const clone = (o) => JSON.parse(JSON.stringify(o));

const playerResponse = () => ({
  responseContext: { serviceTrackingParams: [] },
  playabilityStatus: { status: "OK" },
  streamingData: { expiresInSeconds: "21540", formats: [{ itag: 18, url: "https://rr1.googlevideo.com/x" }] },
  videoDetails: { videoId: "abc123", title: "Real video", lengthSeconds: "212" },
  playbackTracking: { videostatsPlaybackUrl: { baseUrl: "https://s.youtube.com/api/stats/playback" } },
  adPlacements: [{ adPlacementRenderer: { config: { adPlacementConfig: { kind: "AD_PLACEMENT_KIND_START" } } } }],
  playerAds: [{ playerLegacyDesktopWatchAdsRenderer: { playerAdParams: {} } }],
  adSlots: [{ adSlotRenderer: { adSlotMetadata: { slotType: "SLOT_TYPE_PLAYER_BYTES" } } }],
  adBreakHeartbeatParams: "abcdef",
  auxiliaryUi: { messageRenderers: { bkaEnforcementMessageViewModel: { x: 1 }, enforcementMessageViewModel: { x: 1 }, somethingElse: { keep: true } } },
});

const videoItem = (id) => ({ richItemRenderer: { content: { videoRenderer: { videoId: id } } } });
const feed = () => ({
  responseContext: {},
  contents: { twoColumnBrowseResultsRenderer: { tabs: [{ tabRenderer: { content: { richGridRenderer: {
    masthead: { videoMastheadAdV3Renderer: { x: 1 } },
    contents: [
      videoItem("a"),
      { richItemRenderer: { content: { adSlotRenderer: { x: 1 } } } },
      videoItem("b"),
      { richSectionRenderer: { content: { statementBannerRenderer: { x: 1 } } } },
      { adSlotRenderer: { x: 1 } },
      { richItemRenderer: { content: { inFeedAdLayoutRenderer: { x: 1 } } } },
      videoItem("c"),
      { continuationItemRenderer: { token: "t" } },
    ] } } } }] } },
});
const search = () => ({
  responseContext: {},
  contents: { twoColumnSearchResultsRenderer: { primaryContents: { sectionListRenderer: { contents: [{ itemSectionRenderer: { contents: [
    { videoRenderer: { videoId: "s1" } }, { adSlotRenderer: { x: 1 } }, { searchPyvRenderer: { x: 1 } }, { promotedSparklesWebRenderer: { x: 1 } },
    { videoRenderer: { videoId: "s2" } } ] } }] } } } },
});
const next = () => ({
  responseContext: {},
  contents: { twoColumnWatchNextResults: { secondaryResults: { secondaryResults: { results: [
    { compactVideoRenderer: { videoId: "n1" } }, { compactPromotedVideoRenderer: { x: 1 } }, { adSlotRenderer: { x: 1 } },
    { compactVideoRenderer: { videoId: "n2" } }, { itemSectionRenderer: { contents: [{ adSlotRenderer: { x: 1 } }] } } ] } } } },
  playerResponse: playerResponse(),
});
const pbj = () => [{ page: "watch", playerResponse: playerResponse() }, { page: "watch", response: next() }];

module.exports = { clone, playerResponse, feed, search, next, pbj };
