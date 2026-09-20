package li.cil.oc.integration.lwjgl3ify;

import li.cil.oc.client.gui.traits.InputBuffer;
import li.cil.oc.integration.lwjgl3ify.IFileUpload;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_FILE;

@Lwjgl3Aware
class FileUploadSupport implements IFileUpload {
    @Lwjgl3Aware
    class DropFileFilter extends SDL_EventFilter {
        @Override
        public boolean invoke(long userdata, long eventPtr) {
            SDL_Event event = SDL_Event.create(eventPtr);
            if (event.type() == SDL_EVENT_DROP_FILE) {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof InputBuffer) {
                    String file = MemoryUtil.memUTF8Safe(event.drop().data());
                    if (file != null) ((InputBuffer) screen).handleDropFile(file);
                }
            }
            return true;
        }
    }

    DropFileFilter filter = new DropFileFilter();
    @Override
    public void init() {
        SDLEvents.SDL_AddEventWatch(filter, MemoryUtil.NULL);
    }
}