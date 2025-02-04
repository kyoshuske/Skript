package ch.njol.skript.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import ch.njol.skript.Skript;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * @author Peter Güttinger
 */
public class RetainingLogHandler extends LogHandler {

	private final Deque<LogEntry> log = new LinkedList<>();
	private int numErrors = 0;

	boolean printedErrorOrLog = false;

	/**
	 * Internal method for creating a backup of this log.
	 * @return A new RetainingLogHandler containing the contents of this RetainingLogHandler.
	 */
	@ApiStatus.Internal
	@Contract("-> new")
	public RetainingLogHandler backup() {
		RetainingLogHandler copy = new RetainingLogHandler();
		copy.numErrors = this.numErrors;
		copy.printedErrorOrLog = this.printedErrorOrLog;
		copy.log.addAll(this.log);
		return copy;
	}

	/**
	 * Internal method for restoring a backup of this log.
	 */
	@ApiStatus.Internal
	public void restore(RetainingLogHandler copy) {
		this.numErrors = copy.numErrors;
		this.log.clear();
		this.log.addAll(copy.log);
	}

	@Override
	public LogResult log(LogEntry entry) {
		log.add(entry);
		if (entry.getLevel().intValue() >= Level.SEVERE.intValue())
			numErrors++;
		printedErrorOrLog = false;
		return LogResult.CACHED;
	}

	@Override
	public void onStop() {
		if (!printedErrorOrLog && Skript.testing())
			SkriptLogger.LOGGER.warning("Retaining log wasn't instructed to print anything at " + SkriptLogger.getCaller());
	}

	@Override
	public RetainingLogHandler start() {
		SkriptLogger.startLogHandler(this);
		return this;
	}

	public final boolean printErrors() {
		return printErrors(null);
	}

	/**
	 * Prints all retained errors or the given one if no errors were retained.
	 * <p>
	 * This handler is stopped if not already done.
	 *
	 * @param def Error to print if no errors were logged, can be null to not print any error if there are none
	 * @return Whether there were any errors
	 */
	public final boolean printErrors(@Nullable String def) {
		return printErrors(def, ErrorQuality.SEMANTIC_ERROR);
	}

	public final boolean printErrors(@Nullable String def, ErrorQuality quality) {
		assert !printedErrorOrLog;
		printedErrorOrLog = true;
		stop();

		boolean hasError = false;
		for (LogEntry e : log) {
			if (e.getLevel().intValue() >= Level.SEVERE.intValue()) {
				SkriptLogger.log(e);
				hasError = true;
			} else {
				e.discarded("not printed");
			}
		}

		if (!hasError && def != null)
			SkriptLogger.log(SkriptLogger.SEVERE, def);

		return hasError;
	}

	/**
	 * Sends all retained error messages to the given recipient.
	 * <p>
	 * This handler is stopped if not already done.
	 *
	 * @param recipient
	 * @param def Error to send if no errors were logged, can be null to not print any error if there are none
	 * @return Whether there were any errors to send
	 */
	public final boolean printErrors(CommandSender recipient, @Nullable String def) {
		assert !printedErrorOrLog;
		printedErrorOrLog = true;
		stop();

		boolean hasError = false;
		for (LogEntry e : log) {
			if (e.getLevel().intValue() >= Level.SEVERE.intValue()) {
				SkriptLogger.sendFormatted(recipient, e.toFormattedString());
				e.logged();
				hasError = true;
			} else {
				e.discarded("not printed");
			}
		}

		if (!hasError && def != null) {
			SkriptLogger.sendFormatted(recipient, def);
		}
		return hasError;
	}

	/**
	 * Prints all retained log messages.
	 * <p>
	 * This handler is stopped if not already done.
	 */
	public final void printLog() {
		assert !printedErrorOrLog;
		printedErrorOrLog = true;
		stop();
		SkriptLogger.logAll(log);
	}

	public boolean hasErrors() {
		return numErrors != 0;
	}

	@Nullable
	public LogEntry getFirstError() {
		for (LogEntry e : log) {
			if (e.getLevel().intValue() >= Level.SEVERE.intValue())
				return e;
		}
		return null;
	}

	public LogEntry getFirstError(String def) {
		for (LogEntry e : log) {
			if (e.getLevel().intValue() >= Level.SEVERE.intValue())
				return e;
		}
		return new LogEntry(SkriptLogger.SEVERE, def);
	}

	/**
	 * Clears the list of retained log messages.
	 */
	public void clear() {
		for (LogEntry e : log)
			e.discarded("cleared");
		log.clear();
		numErrors = 0;
	}

	public int size() {
		return log.size();
	}

	@SuppressWarnings("null")
	public Collection<LogEntry> getLog() {
		// if something is grabbing the log entries, they're probably handling them manually
		printedErrorOrLog = true;
		return Collections.unmodifiableCollection(log);
	}

	public Collection<LogEntry> getErrors() {
		Collection<LogEntry> r = new ArrayList<>();
		for (LogEntry e : log) {
			if (e.getLevel().intValue() >= Level.SEVERE.intValue())
				r.add(e);
		}
		return r;
	}

	public int getNumErrors() {
		return numErrors;
	}

}
