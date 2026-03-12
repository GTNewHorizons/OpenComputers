package li.cil.oc.integration.lwjgl3ify

import li.cil.oc.client.gui.traits.InputBuffer
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware
import net.minecraft.client.Minecraft
import org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_FILE
import org.lwjgl.sdl.{SDLEvents, SDL_Event, SDL_EventFilter}
import org.lwjgl.system.MemoryUtil


@Lwjgl3Aware class DropFileFilter extends SDL_EventFilter {
  override def invoke(userdata: Long, eventPtr: Long): Boolean = {
    val event = SDL_Event.create(eventPtr)
    if (event.`type` == SDL_EVENT_DROP_FILE) {
      val screen = Minecraft.getMinecraft.currentScreen
      screen match {
        case handler: InputBuffer =>
          val file = MemoryUtil.memUTF8(event.drop().data())
          handler.handleDropFile(file)
        case _ =>
      }
    }
    true
  }
}

@Lwjgl3Aware object FileUploadSupport {
  val filter = new DropFileFilter

  def init(): Unit = {
    SDLEvents.SDL_AddEventWatch(filter, MemoryUtil.NULL)
  }
}