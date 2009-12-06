package org.basex.core;

import static org.basex.core.Text.*;
import java.io.IOException;
import java.io.OutputStream;
import org.basex.core.Commands.CmdPerm;
import org.basex.data.Data;
import org.basex.data.Result;
import org.basex.io.PrintOutput;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.util.Performance;
import org.basex.util.TokenBuilder;

/**
 * This class provides the architecture for all internal command
 * implementations. It evaluates queries that are sent by the GUI, the client or
 * the standalone version.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
public abstract class Proc extends Progress {
  /** Commands flag: standard. */
  public static final int STANDARD = 256;
  /** Commands flag: data reference needed. */
  public static final int DATAREF = 512;

  /** Command arguments. */
  protected String[] args;
  /** Database context. */
  protected Context context;
  /** Database properties. */
  protected Prop prop;

  /** Container for query information. */
  protected TokenBuilder info = new TokenBuilder();
  /** Performance measurements. */
  protected Performance perf;
  /** Temporary query result. */
  protected Result result;

  /** Flags for controlling process evaluation. */
  public final int flags;

  /**
   * Constructor.
   * @param f command flags
   * @param a arguments
   */
  public Proc(final int f, final String... a) {
    flags = f;
    args = a;
  }

  /**
   * Convenience method for executing a process and printing textual results.
   * If an exception occurs, a {@link BaseXException} is thrown.
   * @param ctx database context
   * @param out output stream reference
   * @throws BaseXException exception
   */
  public void exec(final Context ctx, final OutputStream out)
      throws BaseXException {
    if(!execute(ctx, out instanceof PrintOutput ? (PrintOutput) out :
      new PrintOutput(out))) throw new BaseXException(info());
  }

  /**
   * Executes a process. This method must only be used if a command
   * does not generate textual results.
   * @param ctx database context
   * @return success flag
   */
  public final boolean execute(final Context ctx) {
    return execute(ctx, null);
  }

  /**
   * Executes the process, prints the result to the specified output stream
   * and returns a success flag.
   * @param ctx database context
   * @param out output stream
   * @return success flag
   */
  public final boolean execute(final Context ctx, final PrintOutput out) {
    perf = new Performance();
    context = ctx;
    prop = ctx.prop;

    // check data reference
    final Data data = context.data;
    if(data == null && (flags & DATAREF) != 0) return error(PROCNODB);
    // check permissions
    final int i = context.perm(flags & 0xFF, data != null ? data.meta : null);
    if(i != -1) return error(PERMNO, CmdPerm.values()[i]);

    boolean ok = false;
    try {
      ok = exec(out);
    } catch(final ProgressException ex) {
      abort();
      return error(PROGERR);
    } catch(final Throwable ex) {
      Performance.gc(2);
      Main.debug(ex);
      abort();

      if(ex instanceof OutOfMemoryError) return error(PROCOUTMEM);

      final Object[] st = ex.getStackTrace();
      final Object[] obj = new Object[st.length + 1];
      obj[0] = ex.toString();
      System.arraycopy(st, 0, obj, 1, st.length);
      return error(Main.bug(obj));
    }
    return ok;
  }

  /**
   * Returns the query information as a string.
   * @return info string
   */
  public final String info() {
    return info.toString();
  }

  /**
   * Returns the result set, generated by the last query.
   * Must only be called if {@link Prop#CACHEQUERY} is set.
   * @return result set
   */
  public final Result result() {
    return result;
  }

  /**
   * Returns if the command performs updates.
   * @param ctx context reference
   * @return result of check
   */
  @SuppressWarnings("unused")
  public boolean updating(final Context ctx) {
    return false;
  }

  // PROTECTED METHODS ========================================================

  /**
   * Executes a process and serializes the result.
   * @param out output stream
   * @return success of operation
   * @throws IOException I/O exception
   */
  protected abstract boolean exec(final PrintOutput out) throws IOException;

  /**
   * Adds the error message to the message buffer {@link #info}.
   * @param msg error message
   * @param ext error extension
   * @return false
   */
  protected final boolean error(final String msg, final Object... ext) {
    info.reset();
    info.add(msg == null ? "" : msg, ext);
    return false;
  }

  /**
   * Adds information on the process execution.
   * @param str information to be added
   * @param ext extended info
   * @return true
   */
  protected final boolean info(final String str, final Object... ext) {
    if(prop.is(Prop.INFO)) {
      info.add(str, ext);
      info.add(Prop.NL);
    }
    return true;
  }

  /**
   * Performs the first argument as XQuery and returns a node set.
   */
  protected final void queryNodes() {
    try {
      result = new QueryProcessor(args[0], context).queryNodes();
    } catch(final QueryException ex) {
      Main.debug(ex);
      error(ex.getMessage());
    }
  }

  /**
   * Returns the command option.
   * @param typ options enumeration
   * @param <E> token type
   * @return option
   */
  protected final <E extends Enum<E>> E getOption(final Class<E> typ) {
    try {
      return Enum.valueOf(typ, args[0].toUpperCase());
    } catch(final Exception ex) {
      error(CMDWHICH, args[0]);
      return null;
    }
  }

  /**
   * Returns the list of arguments.
   * @return arguments
   */
  protected final String args() {
    final StringBuilder sb = new StringBuilder();
    for(final String a : args) {
      if(a == null || a.isEmpty()) continue;
      sb.append(' ');
      final boolean s = a.indexOf(' ') != -1;
      if(s) sb.append('"');
      sb.append(a);
      if(s) sb.append('"');
    }
    return sb.toString();
  }

  /**
   * Returns a string representation of the process. In the client/server
   * architecture, this string is sent to and reparsed by the server.
   * @return string representation
   */
  @Override
  public String toString() {
    return Main.name(this).toUpperCase() + args();
  }
}
