package org.basex.query.expr;

import static org.basex.query.QueryTokens.*;
import static org.basex.util.Token.*;
import java.io.IOException;
import org.basex.data.Serializer;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.item.QNm;
import org.basex.query.item.Uri;
import org.basex.query.iter.Iter;
import org.basex.query.util.NSLocal;
import org.basex.query.util.Var;

/**
 * Variable expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class VarCall extends Expr {
  /** Variable name. */
  public Var var;

  /**
   * Constructor.
   * @param v variable
   */
  public VarCall(final Var v) {
    var = v;
  }

  @Override
  public Expr comp(final QueryContext ctx) throws QueryException {
    var = ctx.vars.get(var);
    if(var.expr == null) return this;

    // pre-assign static variables
    final NSLocal lc = ctx.ns;
    ctx.ns = lc.copy();
    if(ctx.nsElem.length != 0) ctx.ns.add(new QNm(EMPTY, Uri.uri(ctx.nsElem)));

    /* Choose variables to be pre-evaluated.
     * If a variable is pre-evaluated, it may not be subject to further
     * optimizations (IndexAceess, count, ...). On the other hand, multiple
     * evaluations of the same expression can be avoided here.
     */
    Expr e = var.expr;
    if(ctx.nsElem.length != 0 || lc.size() != 0 || var.type != null ||
        var.global || e instanceof CFrag || e instanceof FunCall) {
      e = var.item(ctx);
    }
    ctx.ns = lc;
    return e;
  }

  /**
   * Checks expression for equality.
   * @param v variable
   * @return result of check
   */
  public boolean eq(final Var v) {
    return var.name.eq(v.name) && var.type == v.type;
  }

  @Override
  public Iter iter(final QueryContext ctx) throws QueryException {
    var = ctx.vars.get(var);
    return var.iter(ctx);
  }

  @Override
  public boolean usesPos(final QueryContext ctx) {
    return var.returned(ctx) == Return.NUM || var.expr == null ||
      var.expr.usesPos(ctx);
  }

  @Override
  public int countVar(final Var v) {
    return v == null || var.eq(v) ? 1 : 0;
  }

  @Override
  public Expr removeVar(final Var v) {
    return var.eq(v) ? new Context() : this;
  }

  @Override
  public Return returned(final QueryContext ctx) {
    return var.returned(ctx);
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.emptyElement(this, VAR, var.name.str());
  }

  @Override
  public String color() {
    return "66DDAA";
  }

  @Override
  public String info() {
    return "Variable";
  }

  @Override
  public String toString() {
    return var.toString();
  }
}
