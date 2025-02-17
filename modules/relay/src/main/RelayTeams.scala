package lila.relay

import chess.format.pgn.*
import chess.{ FideId, PlayerName }

import lila.fide.{ FidePlayer, PlayerToken }
import lila.study.ChapterPreview

type TeamName = String

private class RelayTeamsTextarea(val text: String):

  def sortedText = text.linesIterator.toList.sorted.mkString("\n")

  /* We need this because `PlayerName | FideId` doesn't work
   * the compilear can't differentiate between the two types
   * at runtime using pattern matching. */
  private type PlayerNameStr = String

  lazy val teams: Map[TeamName, List[PlayerNameStr | FideId]] = text.linesIterator
    .take(1000)
    .toList
    .flatMap: line =>
      line.split(';').map(_.trim) match
        case Array(team, player) => Some(team -> (player.toIntOption.fold(player)(FideId(_))))
        case _                   => none
    .groupBy(_._1)
    .view
    .mapValues(_.map(_._2))
    .toMap

  private lazy val tokenizedPlayerTeams: Map[PlayerToken | FideId, TeamName] =
    playerTeams.mapKeys(tokenizePlayer)

  private val tokenizePlayer: PlayerNameStr | FideId => PlayerToken | FideId =
    case name: PlayerNameStr => FidePlayer.tokenize(name)
    // typing with `fideId: FideId` results in a compiler warning. The current code however is ok.
    case fideId => fideId

  private lazy val playerTeams: Map[PlayerNameStr | FideId, TeamName] =
    teams.flatMap: (team, players) =>
      players.map(_ -> team)

  def update(games: RelayGames): RelayGames = games.map: game =>
    game.copy(tags = update(game.tags))

  private def update(tags: Tags): Tags =
    chess.Color.all.foldLeft(tags): (tags, color) =>
      val found = tags
        .fideIds(color)
        .flatMap(findMatching)
        .orElse(PlayerName.raw(tags.names(color)).flatMap(findMatching))
      found.fold(tags): team =>
        tags + Tag(_.teams(color), team)

  private def findMatching(player: PlayerNameStr | FideId): Option[TeamName] =
    playerTeams.get(player).orElse(tokenizedPlayerTeams.get(tokenizePlayer(player)))

final class RelayTeamTable(
    chapterPreviewApi: lila.study.ChapterPreviewApi,
    cacheApi: lila.memo.CacheApi
)(using Executor):

  import play.api.libs.json.*

  def tableJson(relay: RelayRound): Fu[JsonStr] = cache.get(relay.studyId)

  private val cache = cacheApi[StudyId, JsonStr](256, "relay.teamTable"):
    _.expireAfterWrite(3 seconds).buildAsyncFuture(impl.makeJson)

  private object impl:

    import chess.{ Color, Outcome }

    def makeJson(studyId: StudyId): Fu[JsonStr] =
      chapterPreviewApi
        .dataList(studyId)
        .map: chapters =>
          import json.given
          JsonStr(Json.stringify(Json.obj("table" -> makeTable(chapters))))

    case class TeamWithPoints(name: String, points: Float = 0):
      def add(o: Option[Outcome], as: Color) =
        copy(points = points + o.so(_.winner match
          case Some(w) if w == as => 1
          case None               => 0.5f
          case _                  => 0
        ))
    case class Pair[A](a: A, b: A):
      def is(p: Pair[A])                 = (a == p.a && b == p.b) || (a == p.b && b == p.a)
      def map[B](f: A => B)              = Pair(f(a), f(b))
      def bimap[B](f: A => B, g: A => B) = Pair(f(a), g(b))
      def reverse                        = Pair(b, a)

    case class TeamGame(id: StudyChapterId, pov: Color)

    case class TeamMatch(teams: Pair[TeamWithPoints], games: List[TeamGame]):
      def is(teamNames: Pair[TeamName]) = teams.map(_.name).is(teamNames)
      def add(
          chap: ChapterPreview,
          playerAndTeam: Pair[(ChapterPreview.Player, TeamName)],
          outcome: Option[Outcome]
      ) =
        val t0Color = Color.fromWhite(playerAndTeam.a._2 == teams.a.name)
        if t0Color.white then playerAndTeam else playerAndTeam.reverse
        copy(
          games = TeamGame(chap.id, t0Color) :: games,
          teams = teams.bimap(_.add(outcome, t0Color), _.add(outcome, !t0Color))
        )

    def makeTable(chapters: List[ChapterPreview]): List[TeamMatch] =
      chapters.reverse.foldLeft(List.empty[TeamMatch]): (table, chap) =>
        (for
          outcome <- chap.result
          players <- chap.players
          teams   <- players.traverse(_.team).map(_.toPair).map(Pair.apply)
          m0       = table.find(_.is(teams)) | TeamMatch(teams.map(TeamWithPoints(_)), Nil)
          m1       = m0.add(chap, Pair(players.white -> teams.a, players.black -> teams.b), outcome)
          newTable = m1 :: table.filterNot(_.is(teams))
        yield newTable) | table

    object json:
      import lila.common.Json.given
      given [A: Writes]: Writes[Pair[A]] = Writes: p =>
        Json.arr(p.a, p.b)
      given Writes[TeamWithPoints] = Json.writes[TeamWithPoints]
      given Writes[TeamGame]       = Json.writes[TeamGame]
      given Writes[TeamMatch]      = Json.writes[TeamMatch]
