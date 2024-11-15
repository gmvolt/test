package entity;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import Sound_Operator.SoundManager;
import screen.Screen;
import engine.Cooldown;
import engine.Core;
import engine.DrawManager;
import engine.DrawManager.SpriteType;
import engine.GameSettings;
import Enemy.*;
import entity.BossParts;
import clove.ScoreManager;
import entity.BossVariety;
import static java.lang.Math.random;

/**
 * Groups enemy ships into a formation that moves together.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class BossFormation implements Iterable<BossParts> {

    /** Initial position in the x-axis. */
    private static final int INIT_POS_X = 20;
    /** Initial position in the y-axis. */
    private static final int INIT_POS_Y = 100;
    /** Distance between ships. */
    private static final int SEPARATION_DISTANCE = 24;
    /** Proportion of C-type ships. */
    private static final double PROPORTION_C = 0.2;
    /** Proportion of B-type ships. */
    private static final double PROPORTION_B = 0.4;
    /** Lateral speed of the formation. */
    private static final int X_SPEED = 8;
    /** Downwards speed of the formation. */
    private static final int Y_SPEED = 4;
    /** Speed of the bullets shot by the members. */
    private static final int BULLET_SPEED = 4;
    /** Proportion of differences between shooting times. */
    private static final double SHOOTING_VARIANCE = .2;
    /** Margin on the sides of the screen. */
    private static final int SIDE_MARGIN = 20;
    /** Margin on the bottom of the screen. */
    private static final int BOTTOM_MARGIN = 80;
    /** Distance to go down each pass. */
    private static final int DESCENT_DISTANCE = 20;
    /** Minimum speed allowed. */
    private static final int MINIMUM_SPEED = 10;

    /** DrawManager instance. */
    private DrawManager drawManager;
    /** Application logger. */
    private Logger logger;
    /** Screen to draw ships on. */
    private Screen screen;

    private BossParts bossParts;

    private ScoreManager scoreManager;
    private ItemManager itemManager;

    /** List of enemy ships forming the formation. */
    private List<List<BossParts>> bossPartsFormation;
    /** Minimum time between shots. */
    private Cooldown shootingCooldown;
    /** Number of ships in the formation - horizontally. */
    private int nShipsWide;
    /** Number of ships in the formation - vertically. */
    private int nShipsHigh;
    /** Time between shots. */
    private int shootingInterval;
    /** Variance in the time between shots. */
    private int shootingVariance;
    /** Initial ship speed. */
    private int baseSpeed;
    /** Speed of the ships. */
    private int movementSpeed;
    /** Current direction the formation is moving on. */
    private Direction currentDirection;
    /** Direction the formation was moving previously. */
    private Direction previousDirection;
    /** Interval between movements, in frames. */
    private int movementInterval;
    /** Total width of the formation. */
    private int width;
    /** Total height of the formation. */
    private int height;
    /** Position in the x-axis of the upper left corner of the formation. */
    private int positionX;
    /** Position in the y-axis of the upper left corner of the formation. */
    private int positionY;
    /** Width of one ship. */
    private int shipWidth;
    /** Height of one ship. */
    private int shipHeight;
    /**Separate distance of ships */
    private  int separationDistance;
    /** List of ships that are able to shoot. */
    private List<BossParts> shooters;
    /** Number of not destroyed ships. */
    private int shipCount;
    private static SoundManager sm;

    /** Directions the formation can move. */
    private enum Direction {
        /** Movement to the right side of the screen. */
        RIGHT,
        /** Movement to the left side of the screen. */
        LEFT,
        /** Movement to the bottom of the screen. */
        DOWN
    };


    public void setScoreManager (ScoreManager scoreManager){
        this.scoreManager = scoreManager;
    }
    public void setItemManager (ItemManager itemManager){//add by team Enemy
        this.itemManager = itemManager;
    }

    /**
     * Constructor, sets the initial conditions.
     *
     * @param gameSettings
     *            Current game settings.
     */

    public BossFormation(final GameSettings gameSettings, BossVariety bossVariety) {
        this.drawManager = Core.getDrawManager();
        this.logger = Core.getLogger();

        this.bossPartsFormation = new ArrayList<List<BossParts>>();
        this.shooters = new ArrayList<BossParts>();

        this.currentDirection = Direction.RIGHT;
        this.movementInterval = 0;

        this.nShipsWide = gameSettings.getFormationWidth();
        this.nShipsHigh = gameSettings.getFormationHeight();
        this.shootingInterval = gameSettings.getShootingFrecuency();
        this.shootingVariance = (int) (gameSettings.getShootingFrecuency()
                * SHOOTING_VARIANCE);
        this.baseSpeed = gameSettings.getBaseSpeed();
        this.movementSpeed = this.baseSpeed;

        this.positionX = INIT_POS_X;
        this.positionY = INIT_POS_Y;
        this.separationDistance = SEPARATION_DISTANCE;

        this.positionX = INIT_POS_X;
        this.positionY = INIT_POS_Y;

        //Get Boss Settings from BossVariety
        List<SpriteType> spriteTypes = bossVariety.getSpriteTypes();
        int healthPerPart = bossVariety.getHealthPerPart();

        for (int i = 0; i < nShipsWide; i++) {
            List<BossParts> column = new ArrayList<>();
            SpriteType spriteType = spriteTypes.get(i % spriteTypes.size());

            BossParts bossPart = new BossParts(positionX + (SEPARATION_DISTANCE * i), positionY, spriteType, healthPerPart);
            column.add(bossPart);

            if (spriteType != spriteTypes.get(spriteTypes.size() / 2 - 1)) { // adding shooter excluding center
                shooters.add(bossPart);
            }

            bossPartsFormation.add(column);
            shipCount++;
        }

        this.shipWidth = this.bossPartsFormation.get(0).get(0).getWidth();
        this.shipHeight = this.bossPartsFormation.get(0).get(0).getHeight();

        this.width = (this.nShipsWide - 1) * SEPARATION_DISTANCE
                + this.shipWidth;
        this.height = (this.nShipsHigh - 1) * SEPARATION_DISTANCE
                + this.shipHeight;

        for (List<BossParts> column : this.bossPartsFormation)
            this.shooters.add(column.get(0));

        logger.info("BossFormation initialized with position (" + positionX + ", " + positionY + ")");
    }

    /**
     * Associates the formation to a given screen.
     *
     * @param newScreen
     *            Screen to attach.
     */
    public final void attach(final Screen newScreen) {
        screen = newScreen;
    }

    /**
     * Draws every individual component of the formation.
     */
    public final void draw() {
        for (List<BossParts> column : this.bossPartsFormation)
            for (BossParts bossParts : column)
                drawManager.drawEntity(bossParts, bossParts.getPositionX(),
                        bossParts.getPositionY());
    }

    /**
     * Updates the position of the ships.
     */
    public final void update() {
        if(this.shootingCooldown == null) {
            this.shootingCooldown = Core.getVariableCooldown(shootingInterval,
                    shootingVariance);
            this.shootingCooldown.reset();
        }

        cleanUp();

        int movementX = 0;
        int movementY = 0;
        double remainingProportion = (double) this.shipCount
                / (this.nShipsHigh * this.nShipsWide);
        this.movementSpeed = (int) (Math.pow(remainingProportion, 2)
                * this.baseSpeed);
        this.movementSpeed += MINIMUM_SPEED;

        movementInterval++;
        if (movementInterval >= this.movementSpeed) {
            movementInterval = 0;

            boolean isAtBottom = positionY
                    + this.height > screen.getHeight() - BOTTOM_MARGIN;
            boolean isAtRightSide = positionX
                    + this.width >= screen.getWidth() - SIDE_MARGIN;
            boolean isAtLeftSide = positionX <= SIDE_MARGIN;
            boolean isAtHorizontalAltitude = positionY % DESCENT_DISTANCE == 0;

            if (currentDirection == Direction.DOWN) {
                if (isAtHorizontalAltitude)
                    if (previousDirection == Direction.RIGHT) {
                        currentDirection = Direction.LEFT;
                        this.logger.info("Formation now moving left 1");
                    } else {
                        currentDirection = Direction.RIGHT;
                        this.logger.info("Formation now moving right 2");
                    }
            } else if (currentDirection == Direction.LEFT) {
                if (isAtLeftSide)
                    if (!isAtBottom) {
                        previousDirection = currentDirection;
                        currentDirection = Direction.DOWN;
                        this.logger.info("Formation now moving down 3");
                    } else {
                        currentDirection = Direction.RIGHT;
                        this.logger.info("Formation now moving right 4");
                    }
            } else {
                if (isAtRightSide)
                    if (!isAtBottom) {
                        previousDirection = currentDirection;
                        currentDirection = Direction.DOWN;
                        this.logger.info("Formation now moving down 5");
                    } else {
                        currentDirection = Direction.LEFT;
                        this.logger.info("Formation now moving left 6");
                    }
            }

            if (currentDirection == Direction.RIGHT)
                movementX = X_SPEED;
            else if (currentDirection == Direction.LEFT)
                movementX = -X_SPEED;
            else
                movementY = Y_SPEED;

            positionX += movementX;
            positionY += movementY;

            // Cleans explosions.
            List<BossParts> destroyed;
            for (List<BossParts> column : this.bossPartsFormation) {
                destroyed = new ArrayList<BossParts>();
                for (BossParts parts : column) {
                    if (parts != null && parts.isDestroyed()) {
                        destroyed.add(parts);
                        this.logger.info("Removed enemy "
                                + column.indexOf(parts) + " from column "
                                + this.bossPartsFormation.indexOf(column));
                    }
                }
                column.removeAll(destroyed);
            }

            for (List<BossParts> column : this.bossPartsFormation)
                for (BossParts bossParts : column) {
                    bossParts.move(movementX, movementY);
                    bossParts.update();
                }
        }
    }

    /**
     * Cleans empty columns, adjusts the width and height of the formation.
     */
    private void cleanUp() {
        Set<Integer> emptyColumns = new HashSet<Integer>();
        int maxColumn = 0;
        int minPositionY = Integer.MAX_VALUE;
        for (List<BossParts> column : this.bossPartsFormation) {
            if (!column.isEmpty()) {
                // Height of this column
                int columnSize = column.get(column.size() - 1).positionY
                        - this.positionY + this.shipHeight;
                maxColumn = Math.max(maxColumn, columnSize);
                minPositionY = Math.min(minPositionY, column.get(0)
                        .getPositionY());
            } else {
                // Empty column, we remove it.
                emptyColumns.add(this.bossPartsFormation.indexOf(column));
            }
        }
        for (int index : emptyColumns) {
            this.bossPartsFormation.remove(index);
            logger.info("Removed column " + index);
        }

        int leftMostPoint = 0;
        int rightMostPoint = 0;

        for (List<BossParts> column : this.bossPartsFormation) {
            if (!column.isEmpty()) {
                if (leftMostPoint == 0)
                    leftMostPoint = column.get(0).getPositionX();
                rightMostPoint = column.get(0).getPositionX();
            }
        }

        this.width = rightMostPoint - leftMostPoint + this.shipWidth;
        this.height = maxColumn;

        this.positionX = leftMostPoint;
        this.positionY = minPositionY;
    }

    /**
     * Shoots a bullet downwards.
     *
     * @param bullets
     *            Bullets set to add the bullet being shot.
     */
    public final void shoot(final Set<PiercingBullet> bullets) { // Edited by Enemy
        // For now, only ships in the bottom row are able to shoot.
        if (!shooters.isEmpty()) { // Added by team Enemy
            int index = (int) (random() * this.shooters.size());
            BossParts shooter = this.shooters.get(index);
            if (this.shootingCooldown.checkFinished()) {
                this.shootingCooldown.reset();
                sm = SoundManager.getInstance();
                sm.playES("Enemy_Gun_Shot_1_ES");
                bullets.add(PiercingBulletPool.getPiercingBullet( // Edited by Enemy
                        shooter.getPositionX() + shooter.width / 2,
                        shooter.getPositionY(),
                        BULLET_SPEED,
                        0)); // Edited by Enemy
            }
        }
    }

    /**
     * Destroys a ship.
     *
     * @param destroyedShip
     *            Ship to be destroyed.
     */
    public final int[] destroy(final BossParts destroyedShip, boolean isChainExploded) {
        int count = 0;	// number of destroyed enemy
        int point = 0;  // point of destroyed enemy

        boolean destroyAll = (destroyedShip.spriteType.equals(SpriteType.BossAMiddle1) && destroyedShip.getColor() == Color.red) ||
                (destroyedShip.spriteType.equals(SpriteType.BossAMiddle2) && destroyedShip.getColor() == Color.red);

        if (isChainExploded
                && !destroyedShip.spriteType.equals(SpriteType.ExplosiveEnemyShip1)
                && !destroyedShip.spriteType.equals(SpriteType.ExplosiveEnemyShip2)){
            destroyedShip.chainExplode();
        }

        for (List<BossParts> column : this.bossPartsFormation)
            for (int i = 0; i < column.size(); i++)
                if (column.get(i).equals(destroyedShip)) {
                    if(destroyAll){
                        //destroyedShip.chainExplode(); this part can cause bug
                    }
                    BossParts.hit(destroyedShip);
                    this.logger.info("Destroyed ship in ("
                            + this.bossPartsFormation.indexOf(column) + "," + i + ")");
                    point = column.get(i).getPointValue();
                    count += 1;
                }

        // Updates the list of ships that can shoot the player.
        if ((this.shooters.contains(destroyedShip) && !destroyedShip.spriteType.equals(SpriteType.BossAMiddle1)) ||
                (this.shooters.contains(destroyedShip) && !destroyedShip.spriteType.equals(SpriteType.BossAMiddle2))) {
            this.shooters.remove(destroyedShip);
            this.logger.info("Removed destroyed Arm from Boss.");
        }

        this.shipCount -= count;

        int[] returnValue = {count, point};
        return returnValue;
    }



    /**
     * Returns an iterator over the ships in the formation.
     *
     * @return Iterator over the enemy ships.
     */
    @Override
    public final Iterator<BossParts> iterator() {
        Set<BossParts> enemyShipsList = new HashSet<BossParts>();

        for (List<BossParts> column : this.bossPartsFormation)
            for (BossParts bossParts : column)
                enemyShipsList.add(bossParts);

        return enemyShipsList.iterator();
    }

    /**
     * Checks if there are any ships remaining.
     *
     * @return True when all ships have been destroyed.
     */
    public final boolean isEmpty() {
        return this.shipCount <= 0;
    }
}