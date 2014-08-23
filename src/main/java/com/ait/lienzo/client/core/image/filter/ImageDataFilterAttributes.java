/*
   Copyright (c) 2014 Ahome' Innovation Technologies. All rights reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ait.lienzo.client.core.image.filter;

import com.ait.lienzo.client.core.image.filter.ImageDataFilter.FilterConvolveMatrix;
import com.ait.lienzo.client.core.shape.Attributes;
import com.google.gwt.core.client.JavaScriptObject;

public class ImageDataFilterAttributes extends Attributes
{
    public ImageDataFilterAttributes()
    {
        super();
    }

    protected ImageDataFilterAttributes(JavaScriptObject valu)
    {
        super(valu);
    }

    public final void setActive(boolean active)
    {
        put(ImageDataFilterAttribute.ACTIVE.getProperty(), active);
    }

    public final boolean isActive()
    {
        if (isDefined(ImageDataFilterAttribute.ACTIVE))
        {
            return this.getBoolean(ImageDataFilterAttribute.ACTIVE.getProperty());
        }
        return true;
    }

    public final void setMatrix(double... matrix)
    {
        FilterConvolveMatrix mjso = FilterConvolveMatrix.make();

        for (int i = 0; i < matrix.length; i++)
        {
            mjso.push(matrix[i]);
        }
        setMatrix(mjso);
    }

    public final void setMatrix(FilterConvolveMatrix matrix)
    {
        put(ImageDataFilterAttribute.MATRIX.getProperty(), matrix);
    }

    public final FilterConvolveMatrix getMatrix()
    {
        JavaScriptObject mjso = getObject(ImageDataFilterAttribute.MATRIX.getProperty());

        if (null != mjso)
        {
            return mjso.cast();
        }
        return FilterConvolveMatrix.make();
    }
}