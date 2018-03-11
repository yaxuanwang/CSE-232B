import javax.xml.xpath.XPath;
import java.lang.reflect.Array;
import java.util.*;

// join every root node

public class XQueryRewriter {
    private HashMap<String, ArrayList<String>> varGraph = new HashMap<>();
    private ArrayList<ArrayList<String>> tableGraph = new ArrayList<>();
    private HashMap<String, String> varToRoot = new HashMap<>();
    private HashMap<String, String> varToXq = new HashMap<>();
    private HashMap<String, ArrayList<String>> whereXq = new HashMap<>();
    private HashMap<String, ArrayList<String>> condPairs = new HashMap<>();
    private HashMap<String, Boolean> constructed = new HashMap();
    private HashMap<String, Boolean> labeled = new HashMap();
    private String originalReturn = "";
    private String returnString = "";


    public void parseFLWR(XPathParser.XqContext ctx) {
        if ( !(ctx instanceof XPathParser.Xq_FLWRContext) ){
            return;
        }
        XPathParser.ForClauseContext forCtx = ((XPathParser.Xq_FLWRContext)ctx).forClause();
        XPathParser.WhereClauseContext whereCtx = ((XPathParser.Xq_FLWRContext)ctx).whereClause();
        XPathParser.ReturnClauseContext returnCtx = ((XPathParser.Xq_FLWRContext)ctx).returnClause();

        // Union find var
        for (int i=0; i<forCtx.var().size(); i++) {
            String var = forCtx.var(i).NAME().getText();
            String xq = forCtx.xq(i).getText();
            varToXq.put(var, xq);
            if (xq.startsWith("doc")) {
                if (!varGraph.containsKey(var)) {
                    ArrayList<String> newGraph = new ArrayList<>();
                    newGraph.add(var);
                    varGraph.put(var, newGraph);
                    if (!varToRoot.containsKey(var)) {
                        varToRoot.put(var, var);
                    }
                }
            }
            else {
                int end = xq.indexOf('/') == -1? xq.length(): xq.indexOf('/');
                String preVar = xq.substring(xq.indexOf('$')+1, end);
                for (String key: varGraph.keySet()) {
                    if (varGraph.get(key).contains(preVar)) {
                        varGraph.get(key).add(var);
                        varToRoot.put(var, key);
                    }
                }
            }
        }

        // Union find where
        String condXq = whereCtx.cond().getText();
        String[] pairs = condXq.split("and");
        for (String pair: pairs) {
            String[] conds = pair.split("(eq)|(=)");
            // like "john"
            if(!conds[0].contains("$") ||!conds[1].contains("$") ) {
                String varCond = "";
                if(conds[0].contains("$")) {
                    varCond = conds[0].substring(1);
                } else {
                    varCond = conds[1].substring(1);
                }
                if (!whereXq.containsKey(varToRoot.get(varCond))) {
                    ArrayList<String> whereList = new ArrayList<>();
                    whereXq.put(varToRoot.get(varCond), whereList);
                }
                whereXq.get(varToRoot.get(varCond)).add(pair);
            }
            else {
                String var1 = conds[0].substring(1);
                String var2 = conds[1].substring(1);
                String root1 = varToRoot.get(var1);
                String root2 = varToRoot.get(var2);
                if(root1 != root2) {
                    boolean connected = false;
                    if(condPairs.containsKey(root1+ "+" +root2) ||condPairs.containsKey(root2+ "+" +root1)) {
                        if(condPairs.containsKey(root1+ "+" +root2)) {
                            condPairs.get(root1+ "+" +root2).add(pair);
                        }
                        else {
                            condPairs.get(root2+ "+" +root1).add(pair);
                        }
                        continue;
                    }
                    if (!condPairs.containsKey(root1+ "+" +root2)) {
                        ArrayList<String> condPair = new ArrayList<>();
                        condPairs.put(root1+ "+" +root2, condPair);
                    }
                    condPairs.get(root1+ "+" +root2).add(pair);
                    for (int j=0; j<tableGraph.size(); j++) {
                        if (tableGraph.get(j).contains(root1)) {
                            tableGraph.get(j).add(root2);
                            labeled.put(root2, true);
                            connected = true;
                            break;
                        }
                        else if(tableGraph.get(j).contains(root2)){
                            tableGraph.get(j).add(root1);
                            labeled.put(root1, true);
                            connected = true;
                            break;
                        }
                    }
                    if(!connected) {
                        ArrayList<String> newTable = new ArrayList<>();
                        newTable.add(root1);
                        newTable.add(root2);
                        labeled.put(root1, true);
                        labeled.put(root2, true);
                        tableGraph.add(newTable);
                    }
                }
                else {
//                    ArrayList<String> newTable = new ArrayList<>();
//                    newTable.add(root1);
//                    tableGraph.add(newTable);
                    if (!whereXq.containsKey(root1)) {
                        ArrayList<String> whereList = new ArrayList<>();
                        whereXq.put(root1, whereList);
                    }
                    whereXq.get(root1).add(pair);
                }
            }
        }
        // Single Set process
        for (String root: varGraph.keySet()) {
            if (!labeled.containsKey(root)) {
                ArrayList<String> newTable = new ArrayList<>();
                newTable.add(root);
                tableGraph.add(newTable);
            }
        }

        // parse return
        originalReturn = returnCtx.xq().getText();
    }


    public String constructJoin() {
        String joinRet = "" ;
        if (tableGraph.size()==0) return joinRet;
        if (tableGraph.size() ==1) {
            ArrayList<String> currTable = tableGraph.get(0);
            joinRet = setJoin(currTable);
        }
        else {
            ArrayList<String> currTable = tableGraph.get(0);
            String firstSet = setJoin(currTable);
            ArrayList<String> nextTable = tableGraph.get(1);
            String nextSet = setJoin(nextTable);
            String ret = joinTwoSets(firstSet, nextSet);
            joinRet = ret;
            for (int i = 2; i < tableGraph.size(); i++) {
                firstSet = ret;
                nextTable = tableGraph.get(i);
                nextSet = setJoin(nextTable);
                ret = joinTwoSets(firstSet, nextSet);
                joinRet = ret;
            }
        }
        joinRet = "for $tuple in " + joinRet.substring(0, joinRet.length()-3)+"\n";
        joinRet += constructReturn();
        return joinRet;
    }

    public String joinTwoSets(String firstSet, String nextSet) {
        String joinSetsRet = "join ( \n";
        joinSetsRet = joinSetsRet + firstSet + nextSet + "\n [], []),\n\n";
        return joinSetsRet;
    }

    public String setJoin(ArrayList<String> currTable) {
        String joinRet = "";
        if (currTable.size()==1) {
            joinRet = constructJoinEle(currTable.get(0));
            return joinRet;
        }
        for(int k=0; k<currTable.size(); k++) {
            for (int j = 0; j < currTable.size(); j++) {
                String root1 = currTable.get(k);
                String root2 = currTable.get(j);
                if (!condPairs.containsKey(root1 + "+" + root2)) {
                    continue;
                }
                if(constructed.containsKey(root1) && constructed.containsKey(root2)) {
                    continue;
                }
                joinRet = "join ( \n" + joinRet;
                if(!constructed.containsKey(root1)) {
                    joinRet += constructJoinEle(root1);
                    joinRet += "\n\n";
                }
                if(!constructed.containsKey(root2)) {
                    joinRet += constructJoinEle(root2);
                    joinRet += "\n\n";
                }

                joinRet += constructCondEle(root1, root2);
                joinRet += "),\n\n";
            }
        }
        return joinRet;
    }

    public String constructJoinEle(String root) {
        String joinEle = "for ";
        String returnEle = "return <tuple>\n";
        ArrayList<String> currVars= varGraph.get(root);
        for (int i=0; i< currVars.size(); i++) {
            String var = currVars.get(i);
            if(i< currVars.size()-1) {
                joinEle = joinEle + "$" + var + " in " + varToXq.get(var) + "," + "\n";
                returnEle = returnEle + "<" + var + ">{$"+ var + "}" + "</" + var + ">," + "\n";
            }
            else {
                joinEle = joinEle + "$" + var + " in " + varToXq.get(var) + "\n";
                returnEle = returnEle + "<" + var + ">{$"+ var + "}" + "</" + var + ">" + "\n";
            }
        }
        if (whereXq.containsKey(root)) {
            joinEle += "where ";
            for (String whereStr : whereXq.get(root)) {
                String newWhere = "";
                String[] whereList = whereStr.split("(eq)|(=)");
                newWhere = newWhere + whereList[0] + " eq " + whereList[1];
                joinEle = joinEle + newWhere + "\n";
            }
        }
        returnEle += "</tuple>,";
        return joinEle+returnEle;
    }

    public String constructCondEle(String root1, String root2) {
        String condEle1 = "[";
        String condEle2 = "[";
        ArrayList<String> varPairs = condPairs.get(root1+"+"+root2);
        for (int i=0; i<varPairs.size(); i++) {
            String[] vars = varPairs.get(i).split("(eq)|(=)");
            condEle1 = condEle1 + vars[0].substring(1) + ",";
            condEle2 = condEle2 + vars[1].substring(1) + ",";

        }
        constructed.put(root1, true);
        constructed.put(root2, true);
        return condEle1.substring(0,condEle1.length()-1) + "] , " + condEle2.substring(0, condEle2.length()-1) + "]";
    }

    public String constructReturn() {
        String joinReturn = "return <result> {";
        joinReturn += originalReturn.replaceAll("\\$([A-Za-z0-9_]+)", "\\$tuple/$1/*");
        return joinReturn + "} </result>";
    }

}