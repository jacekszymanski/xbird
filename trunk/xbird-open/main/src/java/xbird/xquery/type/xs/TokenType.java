/*
 * @(#)$Id: TokenType.java 3619 2008-03-26 07:23:03Z yui $
 *
 * Copyright 2006-2008 Makoto YUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Contributors:
 *     Makoto YUI - initial implementation
 */
package xbird.xquery.type.xs;

import xbird.xqj.XQJConstants;

/**
 * 
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public class TokenType extends NormalizedStringType {
    private static final long serialVersionUID = 4167316813236698441L;
    public static final String SYMBOL = "xs:token";

    public static final TokenType TOKEN = new TokenType();

    public TokenType() {
        super(SYMBOL);
    }

    protected TokenType(String type) {
        super(type);
    }

    @Override
    public int getXQJBaseType() {
        return XQJConstants.XQBASETYPE_TOKEN;
    }

}
