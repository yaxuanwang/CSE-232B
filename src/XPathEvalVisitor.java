import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Array;
import java.util.*;


public class XPathEvalVisitor extends XPathBaseVisitor<ArrayList<Node>> {
    private ArrayList<Node> curr = new ArrayList();
    private HashMap<String, ArrayList<Node>> varMap = new HashMap<>();
    private Stack<HashMap<String, ArrayList<Node>>> mapStack = new Stack<>();
    private Document inputDoc, outputDoc;
    private boolean joinSign = false;


    @Override
    public ArrayList<Node> visitDoc(XPathParser.DocContext ctx) {
        String filename = ctx.filename().getText();
        filename = filename.substring(1, filename.length() - 1).replace("\"\"", "\"");
        File file = new File(filename);
        ArrayList<Node> ret = new ArrayList();
        Document doc = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(file);
            inputDoc = doc;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inputDoc != null) {
            inputDoc.getDocumentElement().normalize();
            ret.add(inputDoc);
        }
        curr = ret;
        return ret;
    }

    @Override
    public ArrayList<Node> visitAp_all(XPathParser.Ap_allContext ctx) {
        this.visit(ctx.doc());
        LinkedList<Node> tmp = new LinkedList<>(curr);
        ArrayList<Node> ret = new ArrayList<>(curr);
        while (!tmp.isEmpty()) {
            Node n = tmp.poll();
            tmp.addAll(getChildren(n));
            ret.addAll(getChildren(n));
        }
        curr = ret;
        return this.visit(ctx.rp());
    }

    private ArrayList<Node> getChildren(Node n) {
        ArrayList<Node> ret = new ArrayList<>();
        NodeList nList = n.getChildNodes();
        for (int i = 0; i < nList.getLength(); i++) {
            ret.add(nList.item(i));
        }
        return ret;
    }

    @Override
    public ArrayList<Node> visitAp_descendant(XPathParser.Ap_descendantContext ctx) {
        this.visit(ctx.doc());
        curr = visit(ctx.rp());
        return curr;
    }

    //    not sure about the relation of tagName and node
    @Override
    public ArrayList<Node> visitRp_tagName(XPathParser.Rp_tagNameContext ctx) {
        ArrayList<Node> ret = new ArrayList<>();
        for (Node n : curr) {
            for (Node child : getChildren(n)) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) child;
                    if (el.getTagName().equals(ctx.NAME().getText())) {
                        ret.add(child);
                    }
                }
            }
        }
        curr = ret;
        return ret;
    }


    // not sure
    @Override
    public ArrayList<Node> visitRp_star(XPathParser.Rp_starContext ctx) {
        ArrayList<Node> ret = new ArrayList<>();
        for (Node n : curr) {
            for (Node child : getChildren(n)) {
                ret.add(child);
            }
        }
        curr = ret;
        return ret;
    }

    @Override
    public ArrayList<Node> visitRp_dot(XPathParser.Rp_dotContext ctx) {
        return curr;
    }

    @Override
    public ArrayList<Node> visitRp_dotdot(XPathParser.Rp_dotdotContext ctx) {
        HashSet<Node> par = new HashSet<Node>();
        for (Node n : curr) {
            if (n == inputDoc) {
                throw new RuntimeException("No more parent nodes!");
            }
            par.add(n.getParentNode());
        }
        curr = new ArrayList<Node>(par);
        return curr;
    }

    @Override
    public ArrayList<Node> visitRp_text(XPathParser.Rp_textContext ctx) {
        ArrayList<Node> ret = new ArrayList<>();
        for (Node n : curr) {
            for (Node child : getChildren(n)) {
                if (child.getNodeType() == Node.TEXT_NODE && !child.getNodeValue().isEmpty() && !child.getNodeValue().equals("\n")) {
                    ret.add(child);
                }
            }
        }
        return ret;
    }

    // what should we return? what is curr now?
    @Override
    public ArrayList<Node> visitRp_attName(XPathParser.Rp_attNameContext ctx) {
        ArrayList<Node> ret = new ArrayList<>();
        String attrName = ctx.NAME().getText();
        for (Node n : curr) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String attr = ((Element) n).getAttribute(attrName);
            if (attr != "") {
                ret.add(n);
                attr = attrName + "=\"" + attr + "\"";
                System.out.println(attr);
            }
        }
        curr = ret;
        return ret;
    }

    @Override
    public ArrayList<Node> visitRp_paren(XPathParser.Rp_parenContext ctx) {
        return visit(ctx.rp());
    }

    @Override
    public ArrayList<Node> visitRp_descendant(XPathParser.Rp_descendantContext ctx) {
        this.visit(ctx.rp(0));
        return this.visit(ctx.rp(1));
    }

    @Override
    public ArrayList<Node> visitRp_all(XPathParser.Rp_allContext ctx) {
        this.visit(ctx.rp(0));
        LinkedList<Node> tmp = new LinkedList<>(curr);
        ArrayList<Node> ret = new ArrayList<>(curr);
        while (!tmp.isEmpty()) {
            Node n = tmp.poll();
            tmp.addAll(getChildren(n));
            ret.addAll(getChildren(n));
        }
        curr = ret;
        return this.visit(ctx.rp(1));
    }

    @Override
    public ArrayList<Node> visitRp_filter(XPathParser.Rp_filterContext ctx) {
        this.visit(ctx.rp());
        ArrayList<Node> tmp = new ArrayList<Node>(curr);
        ArrayList<Node> ret = new ArrayList<Node>();
        for (Node n : tmp) {
            curr = new ArrayList<Node>();
            curr.add(n);
            ArrayList<Node> filter = visit(ctx.filter());
            if (!filter.isEmpty()) {
                ret.add(n);
            }
        }
        curr = ret;
        return ret;
    }

    @Override
    public ArrayList<Node> visitRp_combine(XPathParser.Rp_combineContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret = visit(ctx.rp(0));
        curr = tmp;
        ret.addAll(visit(ctx.rp(1)));
        curr = ret;
        return ret;
    }

    @Override
    public ArrayList<Node> visitFilter_rp(XPathParser.Filter_rpContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret = visit(ctx.rp());
        curr = tmp;
        return ret;
    }


    @Override
    public ArrayList<Node> visitFilter_equal(XPathParser.Filter_equalContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret1 = this.visit(ctx.rp(0));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret2 = this.visit(ctx.rp(1));
        curr = new ArrayList<>(tmp);
        boolean flag = false;
        for (Node n1 : ret1) {
            for (Node n2 : ret2) {
                if (n1.isEqualNode(n2)) {
                    flag = true;
                    break;
                }
            }
        }
        ArrayList<Node> ret = new ArrayList<>();
        if (flag) ret = curr;
        return ret;
    }

    @Override
    public ArrayList<Node> visitFilter_is(XPathParser.Filter_isContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret1 = this.visit(ctx.rp(0));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret2 = this.visit(ctx.rp(1));
        curr = new ArrayList<>(tmp);
        boolean flag = false;
        for (Node n1 : ret1) {
            for (Node n2 : ret2) {
                if (n1 == n2) {
                    flag = true;
                    break;
                }
            }
        }
        ArrayList<Node> ret = new ArrayList<>();
        if (flag) ret = curr;
        return ret;
    }


    @Override
    public ArrayList<Node> visitFilter_paren(XPathParser.Filter_parenContext ctx) {
        return this.visit(ctx.filter());
    }


    @Override
    public ArrayList<Node> visitFilter_and(XPathParser.Filter_andContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        Set<Node> ret1 = new HashSet<Node>(visit(ctx.filter(0)));
        curr = new ArrayList<>(tmp);
        Set<Node> ret2 = new HashSet<Node>(visit(ctx.filter(0)));
        ret1.retainAll(ret2);
        curr = new ArrayList<>(ret1);
        return curr;
    }

    @Override
    public ArrayList<Node> visitFilter_or(XPathParser.Filter_orContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        Set<Node> ret1 = new HashSet<Node>(visit(ctx.filter(0)));
        curr = new ArrayList<>(tmp);
        Set<Node> ret2 = new HashSet<Node>(visit(ctx.filter(0)));
        ret1.addAll(ret2);
        curr = new ArrayList<>(ret1);
        return curr;
    }

    @Override
    public ArrayList<Node> visitFilter_not(XPathParser.Filter_notContext ctx) {
        ArrayList<Node> tmp = new ArrayList<Node>(curr);
        ArrayList<Node> ret = new ArrayList<Node>();
        for (Node n : tmp) {
            curr = new ArrayList<Node>();
            curr.add(n);
            ArrayList<Node> filter = visit(ctx.filter());
            if (filter.isEmpty()) {
                ret.add(n);
            }
        }
        curr = ret;
        return curr;
    }

    @Override
    public ArrayList<Node> visitXq_var(XPathParser.Xq_varContext ctx) {
        String varName = ctx.var().NAME().getText();
        if (!varMap.containsKey(varName)) {
            throw new RuntimeException("no such variable: " + varName);
        }
        ArrayList<Node> result = new ArrayList<>(varMap.get(varName));
        return result;
    }

    @Override
    public ArrayList<Node> visitXq_strConstant(XPathParser.Xq_strConstantContext ctx) {
        String str = ctx.stringConstant().getText();
        String s = str.substring(1, str.length() - 1);
        ArrayList<Node> result = new ArrayList<>();
        result.add(makeText(s));
        return result;
    }


    @Override
    public ArrayList<Node> visitXq_ap(XPathParser.Xq_apContext ctx) {
        return visit(ctx.ap());
    }


    @Override
    public ArrayList<Node> visitXq_paren(XPathParser.Xq_parenContext ctx) {
        return visit(ctx.xq());
    }

    // do we need to update curr?
    @Override
    public ArrayList<Node> visitXq_combine(XPathParser.Xq_combineContext ctx) {
        HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
        ArrayList<Node> result = visit(ctx.xq(0));
        varMap = new HashMap<>(preMap);
        result.addAll(visit(ctx.xq(1)));
        varMap = new HashMap<>(preMap);
        return result;
    }

    @Override
    public ArrayList<Node> visitXq_descendant(XPathParser.Xq_descendantContext ctx) {
        ArrayList<Node> ret = visit(ctx.xq());
        curr = ret;
        Set<Node> ret1 = new HashSet<>(visit(ctx.rp()));
        ArrayList<Node> result = new ArrayList<>(ret1);
        return result;
    }

    @Override
    public ArrayList<Node> visitXq_all(XPathParser.Xq_allContext ctx) {
        ArrayList<Node> ret = visit(ctx.xq());
        curr = ret;
        LinkedList<Node> tmp = new LinkedList<>(curr);
        ArrayList<Node> res = new ArrayList<>(curr);
        while (!tmp.isEmpty()) {
            Node n = tmp.poll();
            tmp.addAll(getChildren(n));
            res.addAll(getChildren(n));
        }
        curr = res;
        Set<Node> ret1 = new HashSet<Node>(visit(ctx.rp()));
        ArrayList<Node> result = new ArrayList<>(ret1);
        return result;
    }

    @Override
    public ArrayList<Node> visitXq_tag(XPathParser.Xq_tagContext ctx) {
        String tagName = ctx.NAME(0).getText();
        if (!tagName.equals(ctx.NAME(1).getText())) {
            throw new RuntimeException("Invalid tag name");
        }
        ArrayList<Node> ret = visit(ctx.xq());
        ArrayList<Node> result = new ArrayList<>();
        result.add(makeElem(tagName, ret));

        return result;
    }


    @Override
    public ArrayList<Node> visitXq_FLWR(XPathParser.Xq_FLWRContext ctx) {
        HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
        mapStack.push(preMap);

        ArrayList<Node> result = new ArrayList<>();
        FLWRHelper(ctx, 0, result);

        varMap = mapStack.pop();
        curr = result;          // whether need to update
        return result;
    }


    private void FLWRHelper(XPathParser.Xq_FLWRContext ctx, int index, ArrayList<Node> result) {
        if (index >= ctx.forClause().var().size()) {
            if (ctx.letClause() != null) {
                visit(ctx.letClause());
            }
            if (ctx.whereClause() != null) {
                ArrayList<Node> whereRes = visit(ctx.whereClause());
                if (!whereRes.isEmpty()) {
                    result.addAll(visit(ctx.returnClause()));
                }
            } else {
                result.addAll(visit(ctx.returnClause()));
            }
            return;
        }
        ArrayList<Node> ret = visit(ctx.forClause().xq(index));
        String varName = ctx.forClause().var(index).NAME().getText();
        for (Node n : ret) {
            HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
            mapStack.push(preMap);
            ArrayList<Node> tmp = new ArrayList<>();
            tmp.add(n);
            varMap.put(varName, tmp);
            FLWRHelper(ctx, index + 1, result);
            varMap = mapStack.pop();
        }
        return;
    }

    // not sure about the order
    @Override
    public ArrayList<Node> visitXq_let(XPathParser.Xq_letContext ctx) {
        HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
        mapStack.push(preMap);
        visit(ctx.letClause());
        ArrayList<Node> result = visit(ctx.xq());
        varMap = mapStack.pop();
        return result;
    }


    @Override
    public ArrayList<Node> visitLetClause(XPathParser.LetClauseContext ctx) {
        for (int i = 0; i < ctx.var().size(); i++) {
            varMap.put(ctx.var(i).NAME().getText(), visit(ctx.xq(i)));
        }
        return null;
    }

    @Override
    public ArrayList<Node> visitCond_equal(XPathParser.Cond_equalContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret1 = new ArrayList<>(visit(ctx.xq(0)));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret2 = new ArrayList<>(visit(ctx.xq(1)));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret = new ArrayList<>();
//        if(ret1 == null && ret2 == null) {
//            ArrayList<Node> list = new ArrayList<>();
//            Node flag = null;
//            list.add(flag);
//            return list;
//        }
        for (Node n1 : ret1) {
            for (Node n2 : ret2) {
                if (n1.getNodeType() == Node.TEXT_NODE || n2.getNodeType() == Node.TEXT_NODE) {
                    if (n1.getTextContent().equals(n2.getTextContent())) {
                        Node flag = null;
                        ret.add(flag);
                        return ret;
                    }
                }
                if (n1.isEqualNode(n2)) {
                    Node flag = null;
                    ret.add(flag);
                    return ret;
                }
            }
        }
        return ret;
    }

    @Override
    public ArrayList<Node> visitCond_is(XPathParser.Cond_isContext ctx) {
        ArrayList<Node> tmp = new ArrayList<>(curr);
        ArrayList<Node> ret1 = new ArrayList<>(visit(ctx.xq(0)));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret2 = new ArrayList<>(visit(ctx.xq(1)));
        curr = new ArrayList<>(tmp);
        ArrayList<Node> ret = new ArrayList<>();
        for (Node node1 : ret1) {
            for (Node node2 : ret2) {
                if (node1 == node2) {
                    Node flag = null;
                    ret.add(flag);
                    return ret;
                }
            }
        }
        return ret;
    }

    //need to think about it
    @Override
    public ArrayList<Node> visitCond_empty(XPathParser.Cond_emptyContext ctx) {
        ArrayList<Node> result = visit(ctx.xq());
        if (result.isEmpty()) {
            ArrayList<Node> ret = new ArrayList<>();
            Node flag = null;
            ret.add(flag);
            return ret;
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitCond_satisfy(XPathParser.Cond_satisfyContext ctx) {
        HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
        for (int i = 0; i < ctx.var().size(); i++) {
            String v = ctx.var(i).NAME().getText();
            Set<Node> ret = new HashSet<>(visit(ctx.xq(i)));
            ArrayList<Node> res = new ArrayList<>(ret);
            varMap.put(v, res);
        }
        ArrayList<Node> result = new ArrayList<>(visit(ctx.cond()));
        varMap = preMap;
        return result;
    }

    @Override
    public ArrayList<Node> visitCond_paren(XPathParser.Cond_parenContext ctx) {
        return visit(ctx.cond());
    }

    @Override
    public ArrayList<Node> visitCond_and(XPathParser.Cond_andContext ctx) {
        ArrayList<Node> ret1 = visit(ctx.cond(0));
        ArrayList<Node> ret2 = visit(ctx.cond(1));
        if (!ret1.isEmpty() && !ret2.isEmpty()) {
            return ret1;
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitCond_or(XPathParser.Cond_orContext ctx) {
        ArrayList<Node> ret1 = visit(ctx.cond(0));
        ArrayList<Node> ret2 = visit(ctx.cond(1));
        if (!ret1.isEmpty()) {
            return ret1;
        }
        if (!ret2.isEmpty()) {
            return ret2;
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitCond_not(XPathParser.Cond_notContext ctx) {
        ArrayList<Node> result = visit(ctx.cond());
        if (!result.isEmpty()) {
            return new ArrayList<>();
        } else {
            ArrayList<Node> ret = new ArrayList<>();
            Node tmp = null;
            ret.add(tmp);
            return ret;
        }
    }

    @Override
    public ArrayList<Node> visitWhereClause(XPathParser.WhereClauseContext ctx) {
        return visit(ctx.cond());
    }


    @Override
    public ArrayList<Node> visitReturnClause(XPathParser.ReturnClauseContext ctx) {
        return this.visit(ctx.xq());
    }


    private Node makeText(String s) {
        Document doc = inputDoc;
        return doc.createTextNode(s);
    }


    private Node makeElem(String tagName, ArrayList<Node> listOfNode) {
        if (outputDoc == null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                outputDoc = db.newDocument();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Node tag = outputDoc.createElement(tagName);
        for (Node node : listOfNode) {
            if (node != null) {
                Node importedNode = outputDoc.importNode(node, true);
                tag.appendChild(importedNode);
            }
        }
        return tag;
    }

    @Override
    public ArrayList<Node> visitXq_join(XPathParser.Xq_joinContext ctx) {
        return visit(ctx.joinClause());
    }

    @Override
    public ArrayList<Node> visitJoinClause(XPathParser.JoinClauseContext ctx) {
        HashMap<String, ArrayList<Node>> preMap = new HashMap<>(varMap);
        ArrayList<Node> ret1 = visit(ctx.xq(0));
        varMap = new HashMap<>(preMap);
        ArrayList<Node> ret2 = visit(ctx.xq(1));
//        varMap = new HashMap<>(preMap);
        ArrayList<Node> result = new ArrayList<>();

        int attrSize = ctx.attribute(0).NAME().size();


        ArrayList<HashMap<String, ArrayList<Node>>> MapList = new ArrayList<>();
        for (int i = 0; i < attrSize; i++) {
            String attrName = ctx.attribute(0).NAME(i).getText();
            HashMap<String, ArrayList<Node>> attrMap = new HashMap<>();
            for (Node n : ret1) {
                Node key = ((Element) n).getElementsByTagName(attrName).item(0);
                if (((Element) key).getTagName().equals(attrName)) {
                    String keyStr = key.getTextContent();
                    if (!attrMap.containsKey(keyStr)) {
                        ArrayList<Node> tmp = new ArrayList<>();
                        attrMap.put(keyStr, tmp);
                    }
                    attrMap.get(keyStr).add(n);
                }
            }
            MapList.add(attrMap);
        }

        if (attrSize == 0) {
            for (Node n1 : ret1) {
                for (Node n2 : ret2) {
                    ArrayList<Node> newList = new ArrayList<>();
                    newList.addAll(getChildren(n1));
                    newList.addAll(getChildren(n2));
                    result.add(makeElem("tuple", newList));
                }
            }
            return result;
        }

        ArrayList<Node> currNode = new ArrayList<>(ret2);

//        for (int i = 0; i < attrSize; i++) {
//            String attrName1 = ctx.attribute(0).NAME(i).getText();
//            String attrName2 = ctx.attribute(1).NAME(i).getText();
//            Set<Node> tmp = new HashSet<>();
//            for (Node n2 : currNode) {
//                Node compare = ((Element) n2).getElementsByTagName(attrName2).item(0);
//                String compareStr = compare.getTextContent();
//                if (!MapList.get(i).containsKey(compareStr)) {
//                    continue;
//                }
//                Node compareChild = compare.getFirstChild();
//                for (Node n1 : MapList.get(i).get(compareStr)) {
//                    Node base = ((Element) n1).getElementsByTagName(attrName1).item(0);
//                    Node baseChild = base.getFirstChild();
//                    String baseStr = baseChild.getTextContent();
//                    if (compareChild.getNodeType() == Node.TEXT_NODE || baseChild.getNodeType() == Node.TEXT_NODE) {
//                        if (compareChild.getTextContent().equals(baseChild.getTextContent())) {
//                           if (i == 0) {
//                               ArrayList<Node> newList = new ArrayList<>();
//                               for (Node child1: getChildren(n1)) {
//                                   newList.add(child1.cloneNode(true));
//                               }
//                               for (Node child2: getChildren(n2)) {
//                                   newList.add(child2.cloneNode(true));
//                               }
//                                tmp.add(makeElem("tuple", newList));
//                          } else {
//                                tmp.add(n2);
//                            }
//                        }
//                    } else {
//                        if (compareChild.isEqualNode(baseChild)) {
//                            if (i!=0) {
//                                tmp.add(n2);
//                            }
//                            else {
//                                ArrayList<Node> newList = new ArrayList<>();
//                                for (Node child1: getChildren(n1)) {
//                                    newList.add(child1.cloneNode(true));
//                                }
//                                for (Node child2: getChildren(n2)) {
//                                    newList.add(child2.cloneNode(true));
//                                }
////                                newList.addAll(getChildren(n1));
////                                newList.addAll(getChildren(n2));
//                                tmp.add(makeElem("tuple", newList));
//                            }
//                        }
//                    }
//                }
//
//            }
//            currNode = new ArrayList<>(tmp);
//        }

        if (attrSize >= 1) {
            String attrName1 = ctx.attribute(0).NAME(0).getText();
            String attrName2 = ctx.attribute(1).NAME(0).getText();
            Set<Node> tmp = new HashSet<>();
            for (Node n2 : currNode) {
                Node compare = ((Element) n2).getElementsByTagName(attrName2).item(0);
                String compareStr = compare.getTextContent();
                if (!MapList.get(0).containsKey(compareStr)) {
                    continue;
                }
                Node compareChild = compare.getFirstChild();
                for (Node n1 : MapList.get(0).get(compareStr)) {
                    Node base = ((Element) n1).getElementsByTagName(attrName1).item(0);
                    Node baseChild = base.getFirstChild();
                    String baseStr = baseChild.getTextContent();
                    if (compareChild.getNodeType() == Node.TEXT_NODE || baseChild.getNodeType() == Node.TEXT_NODE) {
                        if (compareChild.getTextContent().equals(baseChild.getTextContent())) {
                               ArrayList<Node> newList = new ArrayList<>();
                               for (Node child1: getChildren(n1)) {
                                   newList.add(child1.cloneNode(true));
                               }
                               for (Node child2: getChildren(n2)) {
                                   newList.add(child2.cloneNode(true));
                               }
                                tmp.add(makeElem("tuple", newList));
                        }
                    } else {
                        if (compareChild.isEqualNode(baseChild)) {
                            ArrayList<Node> newList = new ArrayList<>();
                            for (Node child1: getChildren(n1)) {
                                newList.add(child1.cloneNode(true));
                            }
                            for (Node child2: getChildren(n2)) {
                                newList.add(child2.cloneNode(true));
                            }
//                                newList.addAll(getChildren(n1));
//                                newList.addAll(getChildren(n2));
                            tmp.add(makeElem("tuple", newList));
                        }
                    }
                }

            }
            currNode = new ArrayList<>(tmp);
        }

        for (int i=1; i<attrSize; i++) {
            String attrName1 = ctx.attribute(0).NAME(i).getText();
            String attrName2 = ctx.attribute(1).NAME(i).getText();
            Set<Node> tmp = new HashSet<>();
            for (Node n2: currNode) {
                Node compare = ((Element) n2).getElementsByTagName(attrName2).item(0);
                Node base = ((Element) n2).getElementsByTagName(attrName1).item(0);
                if(compare!=null && base!=null) {
                    Node compareChild = compare.getFirstChild();
                    Node baseChild = base.getFirstChild();
                    if (compareChild.getNodeType() == Node.TEXT_NODE || baseChild.getNodeType() == Node.TEXT_NODE) {
                        if (compareChild.getTextContent().equals(baseChild.getTextContent())) {
                            tmp.add(n2);
                        }
                    }
                    else {
                        if (compareChild.isEqualNode(baseChild)) {
                            tmp.add(n2);
                        }
                    }
                }
            }
            currNode = new ArrayList<>(tmp);
        }

        joinSign = true;
        result = currNode;

        return result;
    }

}





