package net.camacraft.bettertoss.server;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads an item class's own right-click code to see whether it throws something: whether its
 * {@code use(Level, Player, InteractionHand)}, or code of its own mod that the method calls, makes a projectile.
 *
 * <p>This is how items are found that no entity type names: a block item that is thrown instead of placed
 * (Spelunkery's glowsticks), one projectile type shared by many items (its pebbles), a projectile that is an arrow
 * underneath rather than a thrown item (the rocks of Spelunker's Charm, as every MCreator "ranged item" is). The
 * class files are read as data and nothing is loaded or run, so a client-only class named in the code does no harm
 * on a server.
 *
 * <p>What counts as throwing: a {@code new} of a class that extends {@code Projectile}, a cast to such a class of
 * another mod (an entity made through its type's factory), or a call to another mod's method that returns one. The
 * search follows calls and lambdas into classes that are not the game's, Forge's or a common library's, up to
 * {@link #MAX_DEPTH} calls deep and {@link #MAX_METHODS} methods in all. {@code use} is matched by its parameter
 * and return types (its name is {@code m_7203_} in production, and class names are the same in both).
 *
 * <p>Pure: it knows no game class, only names, so it runs outside the game against jar files as well.
 */
public final class UseAnalysis
{
    /** {@code Item.use}'s descriptor. */
    public static final String USE_DESCRIPTOR = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;";
    public static final String PROJECTILE = "net/minecraft/world/entity/projectile/Projectile";
    static final int MAX_DEPTH = 6;
    static final int MAX_METHODS = 400;

    /** Calls into these are not followed (the game, Forge, the JDK and libraries every mod shares). */
    private static final String[] NOT_FOLLOWED = {
            "net/minecraft/", "net/minecraftforge/", "com/mojang/", "java/", "javax/", "jdk/", "sun/", "kotlin/", "scala/",
            "com/google/", "it/unimi/", "io/netty/", "org/slf4j/", "org/apache/", "org/joml/", "org/objectweb/",
            "org/spongepowered/", "org/lwjgl/", "org/jetbrains/"
    };

    /** What a class's own use does: the class that declares it, and the projectile class it makes (null: none seen). */
    public record Result(String declaredIn, @Nullable String projectile)
    {
        public boolean throwsSomething()
        {
            return projectile != null;
        }
    }

    private final Function<String, byte[]> bytes;
    @Nullable private final Function<String, Boolean> gameProjectile;
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, Boolean> projectiles = new HashMap<>();
    private final Map<String, Result> results = new HashMap<>();
    private static final ClassNode MISSING = new ClassNode();

    /** @param bytes a class's file by its internal name ({@code a/b/C}), or null where there is none to read */
    public UseAnalysis(Function<String, byte[]> bytes)
    {
        this(bytes, null);
    }

    /**
     * @param bytes          a class's file by its internal name ({@code a/b/C}), or null where there is none to read
     * @param gameProjectile whether one of the game's own classes is a projectile, for where the game's class files
     *                       are not there to read (in the running game: asked of the loaded classes instead)
     */
    public UseAnalysis(Function<String, byte[]> bytes, @Nullable Function<String, Boolean> gameProjectile)
    {
        this.bytes = bytes;
        this.gameProjectile = gameProjectile;
    }

    /**
     * What the right-click of an item of class {@code itemClass} (internal name) does. Null when neither the class
     * nor one of its parents outside the game declares a {@code use} of its own.
     */
    @Nullable
    public Result analyse(String itemClass)
    {
        if (results.containsKey(itemClass)) return results.get(itemClass);
        Result result = null;
        for (String name = itemClass; name != null && !isGameClass(name); ) {
            ClassNode node = node(name);
            if (node == MISSING) break;
            MethodNode use = find(node, null, USE_DESCRIPTOR);
            if (use != null) {
                int[] budget = {MAX_METHODS};
                result = new Result(name, thrownBy(name, use, 0, budget, new HashSet<>()));
                break;
            }
            name = node.superName;
        }
        results.put(itemClass, result);
        return result;
    }

    /** Whether the class {@code name} is {@code ancestor} or extends it (both internal names; classes only, not interfaces). */
    public boolean isA(String name, String ancestor)
    {
        for (int i = 0; name != null && i < 64; i++) {
            if (name.equals(ancestor)) return true;
            if (name.startsWith("java/") || name.startsWith("[")) return false;
            if (gameProjectile != null && isGameClass(name)) {
                // the running game: its own classes are asked, not read
                try {
                    Class<?> cls = Class.forName(name.replace('/', '.'), false, UseAnalysis.class.getClassLoader());
                    Class<?> parent = cls.getSuperclass();
                    name = parent == null ? null : parent.getName().replace('.', '/');
                } catch (Throwable t) {
                    return false;
                }
                continue;
            }
            ClassNode node = node(name);
            if (node == MISSING) return false;
            name = node.superName;
        }
        return false;
    }

    /** Whether the class, by internal name, is {@code Projectile} or extends it. */
    public boolean isProjectile(String name)
    {
        if (name == null || name.startsWith("[")) return false;
        Boolean known = projectiles.get(name);
        if (known != null) return known;
        boolean answer;
        if (name.equals(PROJECTILE)) {
            answer = true;
        } else if (name.equals("java/lang/Object") || name.startsWith("java/")) {
            answer = false;
        } else if (gameProjectile != null && isGameClass(name)) {
            answer = Boolean.TRUE.equals(gameProjectile.apply(name));
        } else {
            ClassNode node = node(name);
            answer = node != MISSING && node.superName != null && isProjectile(node.superName);
        }
        projectiles.put(name, answer);
        return answer;
    }

    /** The projectile class the method makes, itself or through what it calls; null when it makes none. */
    @Nullable
    private String thrownBy(String owner, MethodNode method, int depth, int[] budget, Set<String> seen)
    {
        if (method.instructions == null) return null;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof TypeInsnNode type) {
                if (type.getOpcode() == Opcodes.NEW && isProjectile(type.desc)) return type.desc;
                if (type.getOpcode() == Opcodes.CHECKCAST && !isGameClass(type.desc) && isProjectile(type.desc)) return type.desc;
            } else if (insn instanceof MethodInsnNode call) {
                if (!followed(call.owner)) continue;
                Type returned = Type.getReturnType(call.desc);
                if (returned.getSort() == Type.OBJECT && isProjectile(returned.getInternalName())) return returned.getInternalName();
                String found = follow(call.owner, call.name, call.desc, depth, budget, seen);
                if (found != null) return found;
            } else if (insn instanceof InvokeDynamicInsnNode dynamic && dynamic.bsmArgs != null) {
                for (Object arg : dynamic.bsmArgs) {
                    if (!(arg instanceof Handle handle) || !followed(handle.getOwner())) continue;
                    String found = follow(handle.getOwner(), handle.getName(), handle.getDesc(), depth, budget, seen);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private String follow(String owner, String name, String desc, int depth, int[] budget, Set<String> seen)
    {
        if (depth >= MAX_DEPTH || budget[0] <= 0 || !seen.add(owner + '.' + name + desc)) return null;
        // the method where it is declared: the class named, or a parent of it outside the game
        for (String at = owner; at != null && followed(at); ) {
            ClassNode node = node(at);
            if (node == MISSING) return null;
            MethodNode method = find(node, name, desc);
            if (method != null) {
                budget[0]--;
                return thrownBy(at, method, depth + 1, budget, seen);
            }
            at = node.superName;
        }
        return null;
    }

    /** The method with this descriptor (and name, unless null) that has code; null when the class has none. */
    @Nullable
    private static MethodNode find(ClassNode node, @Nullable String name, String desc)
    {
        if (node.methods == null) return null;
        for (MethodNode method : node.methods) {
            if (!method.desc.equals(desc) || (name != null && !method.name.equals(name))) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            return method;
        }
        return null;
    }

    private ClassNode node(String name)
    {
        ClassNode node = classes.get(name);
        if (node != null) return node;
        node = MISSING;
        try {
            byte[] data = bytes.apply(name);
            if (data != null) {
                ClassNode read = new ClassNode();
                new ClassReader(data).accept(read, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                node = read;
            }
        } catch (Throwable t) {
            node = MISSING; // a class file that can't be read is a class nothing is known about
        }
        classes.put(name, node);
        return node;
    }

    /** The game's own classes: their use is the game's, and a cast to one of them proves nothing. */
    public static boolean isGameClass(String name)
    {
        return name.startsWith("net/minecraft/") || name.startsWith("com/mojang/");
    }

    private static boolean followed(String owner)
    {
        if (owner.startsWith("[")) return false;
        for (String prefix : NOT_FOLLOWED) {
            if (owner.startsWith(prefix)) return false;
        }
        return true;
    }
}
