package li.cil.oc.client.renderer.glsl;

public final class Uniform {

    final ProgramRecipe owner;
    final String name;
    final int slot;

    Uniform(ProgramRecipe owner, String name, int slot) {
        this.owner = owner;
        this.name = name;
        this.slot = slot;
    }

    @Override
    public String toString() {
        return owner.id() + ":" + name;
    }
}
