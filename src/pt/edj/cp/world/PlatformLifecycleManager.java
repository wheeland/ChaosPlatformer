package pt.edj.cp.world;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.noise.basis.ImprovedNoise;
import java.util.Collection;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import pt.edj.cp.app.IngameState;
import pt.edj.cp.input.IMovementListener;
import pt.edj.cp.world.items.Collectable;
import pt.edj.cp.world.platforms.Platform;


public class PlatformLifecycleManager implements IMovementListener {
    
    private Random random = new Random();
        
    private Vector2f zoneSize;
    private Vector2f halfArea;
    
    private ISpawner platformSpawner;
    private ISpawner itemSpawner;
    
    /**
     * Used for zone management
     */
    private static class Position implements Comparable<Position> {
        public int x;
        public int y;
        
        public Position(int xx, int yy) {
            x = xx;
            y = yy;
        }

        public int compareTo(Position o) {
            if (x != o.x)
                return x - o.x;
            return y - o.y;
        }
    }
    
    
    /**
     * Used for zonemanagement
     */
    private static class IntRect implements Comparable<IntRect> {
        public Position p1;
        public Position p2;
        
        private void addCol(Collection<Position> into, int x) {
            for (int y = p1.y; y <= p2.y; y++)
                into.add(new Position(x, y));
        }
        
        private void addRow(Collection<Position> into, int y) {
            for (int x = p1.x; x <= p2.x; x++)
                into.add(new Position(x, y));
        }
        
        public int compareTo(IntRect o) {
            int d1 = p1.compareTo(o.p1);
            if (d1 != 0) return d1;
            return p2.compareTo(o.p2);
        }
        
        // Returns all positions within this rect that are not included in the
        // other one
        public Iterable<Position> diff(IntRect other) {
            TreeSet<Position> result = new TreeSet<Position>();
            for (int x = p1.x; x < other.p1.x; x++) addCol(result, x);
            for (int x = other.p2.x + 1; x <= p2.x; x++) addCol(result, x);
            for (int y = p1.y; y < other.p1.y; y++) addRow(result, y);
            for (int y = other.p2.y + 1; y <= p2.y; y++) addRow(result, y);
            return result;
        }
        
        // returns all positions in this rectangle
        public Iterable<Position> allPositions() {
            TreeSet<Position> result = new TreeSet<Position>();
            for (int x = p1.x; x <= p2.x; x++) addCol(result, x);
            return result;
        }
    }
    
    
    /**
     * Basic spatial units to decide on platform creation, placement and
     * destruction
     */
    private class Zone extends Position {
        public Platform platform;
        public Collectable collectable;
        
        public Zone(Position p) {
            super(p.x, p.y);
            
            if (platformSpawner.shouldPlacePlatform(p)) {
                platform = ingameState.addPlatform(generatePlatformPosition(p));
                collectable = null;
            } else {
                platform = null;
                collectable = (itemSpawner.shouldPlacePlatform(p))
                        ? ingameState.addCollectable(generatePlatformPosition(p))
                        : null;
            }
        }
        
        public void delete() {
            if (platform != null)
                ingameState.removePlatform(platform);
            if (collectable != null)
                ingameState.removeCollectable(collectable);
        }
    }

    
    private IntRect getActiveZonesForPosition(Vector2f playerPos) {
        Vector2f p1 = playerPos.subtract(halfArea);
        Vector2f p2 = playerPos.add(halfArea);
        
        float sx = zoneSize.x;
        float sy = zoneSize.y;
        
        IntRect result = new IntRect();
        result.p1 = new Position((int) Math.floor(p1.x / sx), (int) Math.floor(p1.y / sy));
        result.p2 = new Position((int) Math.ceil(p2.x / sx), (int) Math.ceil(p2.y / sy));
        return result;
    } 
    
    
    private TreeMap<Position,Zone> zones = new TreeMap<Position,Zone>();
    private IntRect activeZones;
    
    IngameState ingameState; 
    
    
    public PlatformLifecycleManager(IngameState ingame, Vector2f zoneSize, Vector2f activeArea) {
        this.zoneSize = zoneSize;
        this.halfArea = activeArea.mult(0.5f);
        
        this.ingameState = ingame;
        
//        this.platformSpawner = new NoisePlatformSpawner(0.8525342f);
//        this.platformSpawner = new HorizontalPlatformSpawner();
        this.platformSpawner = new RegionSpawner();
        this.itemSpawner = new RandomSpawner(0.7f);
        
        // add initial zones around player
        activeZones = getActiveZonesForPosition(new Vector2f(0.0f, 0.0f));
        for (Position pos : activeZones.allPositions()) {
            zones.put(pos, new Zone(pos));
        }
    }
    
    
    public void movement(Vector3f newPosition, Vector3f delta) {
        Vector2f playerPos = new Vector2f(newPosition.x, newPosition.y);
        IntRect newActiveZones = getActiveZonesForPosition(playerPos);
        
        if (activeZones.compareTo(newActiveZones) != 0) {
            // see if we have to delete old zones
            for (Position pos : activeZones.diff(newActiveZones)) {
                zones.remove(pos).delete();
            }

            // add new zones?
            for (Position pos : newActiveZones.diff(activeZones)) {
                zones.put(pos, new Zone(pos));
            }

            activeZones = newActiveZones;
        }
    }
    
    
    /**
     * Decide whether or not to place a new platform at the given spot
     */
    private interface ISpawner {
        public boolean shouldPlacePlatform(Position zonePosition);
    }
    
    /**
     * Use random choice to make choice for each zone
     */
    private class RandomSpawner implements ISpawner {
        private float threshold;
        
        public RandomSpawner(float th) {
            threshold = th;
        }
        
        public boolean shouldPlacePlatform(Position zonePosition) {
            return random.nextFloat() < threshold;
        }
    }
    
    /**
     * Use noise to create more cluster-like platform density
     */
    private class NoiseSpawner implements ISpawner {
        float stretch;
        private float threshold;
        
        public NoiseSpawner(float stretch, float th) {
            this.stretch = stretch;
            this.threshold = th;
        }
        
        public boolean shouldPlacePlatform(Position zonePosition) {
            float v = ImprovedNoise.noise(zonePosition.x / stretch, zonePosition.y / stretch, 0.0f);
            return (v + 0.5f) < threshold;
        }
    }
    
    /**
     * Generate horizontal platform lines
     */
    private class HorizontalSpawner implements ISpawner {
        public boolean shouldPlacePlatform(Position zonePosition) {
            // get y-dependent spacing
            Random yRand = new Random(zonePosition.y);
            int period = 30 + yRand.nextInt(10);
            int offset = yRand.nextInt(period);
            
            // get x-dependent length
            int number = (zonePosition.x - offset) / period;
            Random xRand = new Random(zonePosition.y + number);
            int length = 10 + xRand.nextInt(6);
            
            int within = (zonePosition.x - offset) % period;
            within = (within + period) % period;
            return within < length;
        }
    }
    
    
    private static class RegionSpawner implements ISpawner {
        private static final int regionSizeX = 4;
        private static final int regionSizeY = 3;
            
        static int posMod(int a, int b) {
            return ((a % b) + b) % b;
        }

        public RegionSpawner() {
        }
        
        private class Region {
            int x;
            int y;
            int within;
            int segmentLen;
            boolean exists;
            boolean connectionUp;
            
            public Region(Position pos) {
                this((int) Math.floor((float) pos.x / regionSizeX),
                        (int) Math.floor((float) pos.y / regionSizeY));
            }
            
            public Region(int xx, int yy) {
                x = xx;
                y = yy;
                
                // get y-dependent spacing
                Random yRand = new Random(6243327 * y);
                float period = 4.5f + 2.f * yRand.nextFloat();
                int offset = yRand.nextInt((int) period);

                // get x-dependent length
                int number = (int) Math.floor((x - offset) / period);
                Random xRand = new Random(6243327 * y + 14327 * number);
                segmentLen = 2 + xRand.nextInt((int)period - 2);
                
                // within slice:
                within = (int) (x - offset - number * period);
                exists = (within < segmentLen);
                connectionUp = xRand.nextBoolean();
            }
            
            Region neighbor(int dx, int dy) {
                return new Region(x + dx, y + dy);
            }
            
            boolean hasLeft() {
                return within > 0;
            }
            
            boolean hasRight() {
                return within < (segmentLen-1);
            }
            
            boolean hasVerticalConnection() {
                if (!exists)
                    return false;
                
                Region other = connectionUp ? new Region(x, y+1) : new Region(x, y-1);
                if (!other.exists || other.connectionUp == connectionUp)
                    return false;
                
                return true;
            }
        }

        public boolean shouldPlacePlatform(Position zonePosition) {
            Region thisRegion = new Region(zonePosition);
            
            if (!thisRegion.exists)
                return false;
            
            int relX = posMod(zonePosition.x, regionSizeX);
            int relY = posMod(zonePosition.y, regionSizeY);
            
            // is this coordinate on the slide of this (existing) region?
            if (relY == 1) {
                if (thisRegion.hasLeft() && relX < 3
                        || thisRegion.hasRight() && relX > 0
                        || relX > 0 && relX < 3)
                    return true;
                return true;
            }
            
            // is there a connection to another plaform?
            if (thisRegion.hasVerticalConnection()) {
                boolean goesUp = thisRegion.connectionUp;
                boolean goesLeft = thisRegion.y % 2 == 0;
                        
                // generate ladder position
                int y = goesUp ? 2 : 0;
                int x = goesLeft ? 1 : 2;
                if (relX == x && relY == y)
                    return true;
            }
            
            
            return false;
        }
        
    }
    
    
    
    Vector3f generatePlatformPosition(Position zonePosition) {
        float maxDiff = 0.15f;
        float x = (zonePosition.x + maxDiff * (2 * random.nextFloat() - 1)) * zoneSize.x;
        float y = (zonePosition.y + maxDiff * (2 * random.nextFloat() - 1)) * zoneSize.y;
        return new Vector3f(x, y, 0.0f);
    }
}