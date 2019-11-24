package sim;

import java.lang.IndexOutOfBoundsException;
import java.lang.IllegalArgumentException;
import java.lang.IllegalStateException;
import java.util.stream.Stream;
import java.util.Random;

public class SimImpl implements SimInterface {
	// Position and direction of robot.
	private int x;
	private int y;
	private Direction dir;

	// Map (duh).
	private final Map map;

	// RNG for simulating imperfect motion.
	private final Random rng;

	// Boundary values for determining imperfect motion occurrence.
	// On each move_front call, rng.nextFloat() is called; If the result is
	// less than ipm_0_boundary, the robot does not move. if the result is larger
	// than ipm_2_boundary, the robot moves two cells forward (if possible).
	private float ipm_0_boundary;
	private float ipm_2_boundary;

	// Construction uses builder pattern, to make user code easier to read..
	// Note that there are no public constructors; Builder is the only way to create
	// SimImpl outside this class.
	private SimImpl(Builder builder) {
		// Set robot's position and direction.
		this.x = builder.x;
		this.y = builder.y;
		this.dir = builder.dir;

		// Create map.
		this.map = new Map(builder.map_width, builder.map_height);

		// Iterate through hazard coordinates, and mark them on the map appropriately.
		// If the coordinate sequence is null, consider it empty.
		if (builder.hazards != null) {
			builder.hazards.forEach(coord -> { // type of coord is Coordinates, defined below.
				// It's an error if robot's starting position is Hazard.
				if (coord.x == this.x && coord.y == this.y) {
					throw new IllegalArgumentException(
							String.format("Overlapping coordinates (%d, %d) for hazard and initial robot position",
									coord.x, coord.y));
				}
				this.map.set(coord.x, coord.y, Cell.HAZARD);
			});
		}

		// Same for blobs. If any overlap with hazard, it's an error.
		// It's fine if it overlaps with robot's initial position, though.
		if (builder.blobs != null) {
			builder.blobs.forEach(coord -> {
				if (this.map.get(coord.x, coord.y).equals(Cell.HAZARD)) {
					throw new IllegalArgumentException(String
							.format("Overlapping coordinates (%d, %d) for hazards and color blobs", coord.x, coord.y));
				}
				this.map.set(x, y, Cell.COLOR_BLOB);
			});
		}

		if (builder.rng == null) {
			this.rng = new Random();
		} else {
			this.rng = builder.rng;
		}

		this.ipm_0_boundary = builder.ipm_0_prob;
		this.ipm_2_boundary = 1.0f - builder.ipm_2_prob;
	}

	// Implementation of SimInterface.
	// For the description of these methods, see the interface side's comments.

	@Override
	public int x() {
		return this.x;
	}

	@Override
	public int y() {
		return this.y;
	}

	@Override
	public boolean move_forward() {
		// coordinates of target cell.
		int target_x = this.x + this.dir.x();
		int target_y = this.y + this.dir.y();

		if (this.hazard_or_oob(target_x, target_y)) {
			return false;
		}

		// Get a random number in [0.0f, 1.0f)...
		float r = this.rng.nextFloat();

		// If not larger than ipm_0_boundary, do nothing.
		if (r > this.ipm_0_boundary) {
			if (r > this.ipm_2_boundary) {
				// Check the cell two cells forward. If not OOB or Hazard,
				// Move to that cell.
				int imp_x = target_x + this.dir.x();
				int imp_y = target_y + this.dir.y();
				if (!this.hazard_or_oob(imp_x, imp_y)) {
					target_x = imp_x;
					target_y = imp_y;
				}
			}
			this.x = target_x;
			this.y = target_y;
		}
		return true;
	}

	@Override
	public void turn_cw() {
		this.dir = this.dir.next_clockwise();
	}

	@Override
	public boolean detect_hazard() {
		// NOTE: we'll consider OOB case a hazard, since
		// they're pretty much the same in that it's an error to
		// try to move the robot to that cell.
		return this.hazard_or_oob(this.x + this.dir.x(), this.y + this.dir.y());
	}

	@Override
	public boolean[] detect_blobs() {
		boolean[] ret = new boolean[4];
		Direction d = Direction.N;
		for (int i = 0; i < 4; i++) {
			int adj_x = this.x + d.x();
			int adj_y = this.y + d.y();
			try {
				ret[i] = this.map.get(adj_x, adj_y).equals(Cell.COLOR_BLOB);
			} catch (IndexOutOfBoundsException e) {
				ret[i] = false;
			}
			d = d.next_clockwise();
		}
		return ret;
	}

	// End of SimInterface Implementation

	// Checks if given coordinate is a hazard or out-of-bound.
	// Useful for checking whether the robot can move into that cell.
	private boolean hazard_or_oob(int x, int y) {
		try {
			return this.map.get(x, y).equals(Cell.HAZARD);
		} catch (IndexOutOfBoundsException e) {
			return true;
		}
	}

	// Returns the direction the robot is facing.
	// This method is not specified in the specification and SimInterface, but it's
	// useful nonetheless.
	public Direction direction() {
		return this.dir;
	}

	// Returns the map's width.
	// This method is not specified in the specification and SimInterface, but it's
	// useful nonetheless.
	public int map_width() {
		return this.map.width();
	}

	// Returns the map's height.
	// This method is not specified in the specification and SimInterface, but it's
	// useful nonetheless.
	public int map_height() {
		return this.map_height();
	}

	// A pair of x coordinate and y coordinate.
	// Used for passing in hazard and color blob coordinates via Stream.
	public static class Coordinates {
		private final int x;
		private final int y;

		public Coordinates(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	// Builder class for SimImpl.
	public static class Builder {
		// Width and Height for the map, and a flag whether these have been set.
		private int map_width;
		private int map_height;
		private boolean map_size_set;

		private int x;
		private int y;
		private boolean robot_position_set;

		private Direction dir;

		private Stream<Coordinates> hazards;
		private Stream<Coordinates> blobs;

		// RNG for simulating imperfect motion.
		private Random rng;
		// Probability of imperfect motion where move_forward doesn't move the robot
		// forward.
		private float ipm_0_prob = 0.1f;
		// Probability of imperfect motion where move_forward moves the robot two cells
		// forward.
		private float ipm_2_prob = 0.1f;

		public Builder() {
			// Note: default values are false for boolean,
			// null for non-primitives. 0 for int, but that's irrelevant.
		}

		// Set the size of the map.
		public void map_size(int map_width, int map_height) {
			if (map_width <= 0 || map_height <= 0) {
				throw new IllegalArgumentException(String.format("Illegal Map Size: (%d, %d)", map_width, map_height));
			}
			this.map_width = map_width;
			this.map_height = map_height;
			this.map_size_set = true;
		}

		// Set the robot's initial position.
		public void robot_pos(int x, int y) {
			if (x < 0) {
				throw new IllegalArgumentException(String.format("Negative initial x coordinate: %d", x));
			}
			if (x < 0) {
				throw new IllegalArgumentException(String.format("Negative initial y coordinate: %d", y));
			}
			this.x = x;
			this.y = y;
			this.robot_position_set = true;
		}

		// Set the initial direction the robot is facing.
		public void robot_dir(Direction dir) {
			this.dir = dir;
		}

		// Set the list of hazards. If not set, no hazards will be placed.
		public void hazards(Stream<Coordinates> hazards) {
			this.hazards = hazards;
		}

		// Set the list of color blobs. If not set, no color blobs will be placed.
		public void blobs(Stream<Coordinates> blobs) {
			this.blobs = blobs;
		}

		// Set the RNG for simulating imperfect motion.
		public void rng(Random rng) {
			this.rng = rng;
		}

		// Set the probability of imperfect motion where move_forward doesn't move the
		// robot happening.
		// Default value is 0.1 (10%).
		public void set_no_movement_probability(float prob) {
			if (prob < 0.0f || 1.0f < prob) {
				throw new IllegalArgumentException(String.format("Invalid probability: %f", prob));
			}
			this.ipm_0_prob = prob;
		}

		// Set the probability of imperfect motion where move_forward doesn't move the
		// robot happening.
		// Default value is 0.1 (10%).
		public void set_double_forward_probability(float prob) {
			if (prob < 0.0f || 1.0f < prob) {
				throw new IllegalArgumentException(String.format("Invalid probability: %f", prob));
			}
			this.ipm_2_prob = prob;
		}

		public SimImpl build() {
			if (!this.map_size_set) {
				throw new IllegalStateException("Size of the map has not been set");
			}

			if (!this.robot_position_set) {
				throw new IllegalStateException("Position of the robot has not been set");
			}

			if (this.dir == null) {
				throw new IllegalStateException("Direction the robot is facing has not been set");
			}

			if (this.map_width <= this.x || this.map_height <= this.y) {
				throw new IndexOutOfBoundsException(
						String.format("Coordinates (%d, %d) out of bounds for map size (%d, %d)", this.x, this.y,
								this.map_width, this.map_height));
			}

			if (this.ipm_0_prob > 1.0f - this.ipm_2_prob) {
				throw new IllegalArgumentException(String.format("Invalid sum for imperfect motion probabilities: %f",
						this.ipm_0_prob + this.ipm_2_prob));
			}

			return new SimImpl(this);
		}
	}
}
