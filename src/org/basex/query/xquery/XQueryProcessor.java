package org.basex.query.xquery;

import org.basex.data.Nodes;
import org.basex.io.IO;
import org.basex.query.QueryProcessor;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.iter.Iter;

/**
 * This is the main class of the XQuery Processor.
 * 
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class XQueryProcessor extends QueryProcessor {
  /** XQuery context reference. */
  public XQContext ctx = new XQContext();
  
  /**
   * XQuery Constructor.
   * @param qu query
   */
  public XQueryProcessor(final String qu) {
    super(qu);
  }
  
  /**
   * XQuery Constructor.
   * @param qu query
   * @param f query file reference
   */
  public XQueryProcessor(final String qu, final IO f) {
    this(qu);
    ctx.file = f;
  }

  @Override
  public QueryContext create() throws QueryException {
    parse(query);
    return ctx;
  }

  /**
   * Parses the specified input.
   * @param in input to be parsed
   * @throws QueryException query exception
   */
  public void parse(final String in) throws QueryException {
    ctx.parse(in);
  }
  
  /**
   * Returns a result iterator.
   * @param nodes node input
   * @return result iterator
   * @throws QueryException query exception
   */
  public Item eval(final Nodes nodes) throws QueryException {
    return iter(nodes).finish();
  }
  
  /**
   * Returns a result iterator.
   * @param nodes node input
   * @return result iterator
   * @throws QueryException query exception
   */
  public Iter iter(final Nodes nodes) throws QueryException {
    if(!compiled) compile(nodes);
    return ctx.iter();
  }

  /**
   * Adds a module reference.
   * @param ns module namespace
   * @param file file name
   */
  public void module(final String ns, final String file) {
    ctx.modules.add(ns);
    ctx.modules.add(file);
  }
  
  /**
   * Sets a new query. Should be called before parsing the query.
   * @param qu query
   */
  public void setQuery(final String qu) {
    query = qu;;
  }
}
