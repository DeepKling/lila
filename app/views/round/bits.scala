package views.html
package round

import chess.variant.{ Crazyhouse, Variant }
import controllers.routes

import scala.util.chaining.*

import lila.app.templating.Environment.{ *, given }
import lila.app.ui.ScalatagsTemplate.{ *, given }
import lila.common.Json.given
import lila.common.LangPath
import lila.game.{ Game, Pov }

object bits:

  def layout(
      variant: Variant,
      title: String,
      pageModule: Option[PageModule],
      moreJs: Frag = emptyFrag,
      openGraph: Option[lila.app.ui.OpenGraph] = None,
      moreCss: Frag = emptyFrag,
      playing: Boolean = false,
      zenable: Boolean = false,
      robots: Boolean = false,
      withHrefLangs: Option[LangPath] = None
  )(body: Frag)(using ctx: PageContext) =
    views.html.base.layout(
      title = title,
      openGraph = openGraph,
      moreJs = moreJs,
      moreCss = frag(
        cssTag(if variant == Crazyhouse then "round.zh" else "round"),
        ctx.pref.hasKeyboardMove.option(cssTag("keyboardMove")),
        ctx.pref.hasVoice.option(cssTag("voice")),
        ctx.blind.option(cssTag("round.nvui")),
        moreCss
      ),
      pageModule = pageModule,
      playing = playing,
      zenable = zenable,
      robots = robots,
      zoomable = true,
      csp = defaultCsp.withPeer.withWebAssembly.some,
      withHrefLangs = withHrefLangs
    )(body)

  def crosstable(cross: Option[lila.game.Crosstable.WithMatchup], game: Game)(using ctx: Context) =
    cross.map: c =>
      views.html.game.crosstable(ctx.userId.fold(c)(c.fromPov), game.id.some)

  def underchat(game: Game)(using ctx: Context) =
    frag(
      views.html.chat.spectatorsFrag,
      isGranted(_.ViewBlurs).option(
        div(cls := "round__mod")(
          game.players.all
            .filter(p => game.playerBlurPercent(p.color) > 30)
            .map { p =>
              div(
                playerLink(
                  p,
                  cssClass = s"is color-icon ${p.color.name}".some,
                  withOnline = false,
                  mod = true
                ),
                s" ${p.blurs.nb}/${game.playerMoves(p.color)} blurs ",
                strong(game.playerBlurPercent(p.color), "%")
              )
            }
            // game.players flatMap { p => p.holdAlert.map(p ->) } map {
            //   case (p, h) => div(
            //     playerLink(p, cssClass = s"is color-icon ${p.color.name}".some, mod = true, withOnline = false),
            //     "hold alert",
            //     br,
            //     s"(ply: ${h.ply}, mean: ${h.mean} ms, SD: ${h.sd})"
            //   )
            // }
        )
      )
    )

  def others(playing: List[Pov], simul: Option[lila.simul.Simul])(using Context) =
    frag(
      h3(
        simul.fold(trans.currentGames()): s =>
          span(cls := "simul")(
            a(href := routes.Simul.show(s.id))("SIMUL"),
            span(cls := "win")(s.wins, " W"),
            " / ",
            span(cls := "draw")(s.draws, " D"),
            " / ",
            span(cls := "loss")(s.losses, " L"),
            " / ",
            s.ongoing,
            " ongoing"
          ),
        "round-toggle-autoswitch".pipe: id =>
          span(cls := "move-on switcher", st.title := trans.automaticallyProceedToNextGameAfterMoving.txt())(
            label(`for` := id)(trans.autoSwitch()),
            span(cls := "switch")(form3.cmnToggle(id, id, checked = false))
          )
      ),
      div(cls := "now-playing"):
        val (myTurn, otherTurn) = playing.partition(_.isMyTurn)
        (myTurn ++ otherTurn.take(6 - myTurn.size))
          .take(9)
          .map: pov =>
            a(href := routes.Round.player(pov.fullId), cls := pov.isMyTurn.option("my_turn"))(
              span(
                cls := s"mini-game mini-game--init ${pov.game.variant.key} is2d",
                views.html.game.mini.renderState(pov)
              )(views.html.game.mini.cgWrap),
              span(cls := "meta")(
                playerUsername(
                  pov.opponent.light,
                  pov.opponent.userId.flatMap(lightUser),
                  withRating = false,
                  withTitle = true
                ),
                span(cls := "indicator")(
                  if pov.isMyTurn then
                    pov.remainingSeconds
                      .fold[Frag](trans.yourTurn())(secondsFromNow(_, alwaysRelative = true))
                  else nbsp
                )
              )
            )
    )

  private[round] def side(
      pov: Pov,
      data: play.api.libs.json.JsObject,
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean
  )(using Context) =
    views.html.game.side(
      pov,
      (data \ "game" \ "initialFen").asOpt[chess.format.Fen.Epd],
      tour,
      simul = simul,
      userTv = userTv,
      bookmarked = bookmarked
    )

  def roundAppPreload(pov: Pov)(using Context) =
    div(cls := "round__app")(
      div(cls := "round__app__board main-board")(chessground(pov)),
      div(cls := "col1-rmoves-preload")
    )
