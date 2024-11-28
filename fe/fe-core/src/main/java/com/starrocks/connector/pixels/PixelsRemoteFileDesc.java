// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.connector.pixels;

import com.starrocks.connector.RemoteFileDesc;
import com.starrocks.connector.paimon.PaimonSplitsInfo;
import io.pixelsdb.pixels.common.metadata.domain.Layout;

import java.util.List;

public class PixelsRemoteFileDesc extends RemoteFileDesc {
    private final List<Layout> pixelsLayouts;
    private PixelsRemoteFileDesc(List<Layout> pixelsLayouts) {
        super(null, null, 0, 0, null);
        this.pixelsLayouts = pixelsLayouts;
    }

    public static PixelsRemoteFileDesc createPamonRemoteFileDesc(List<Layout> pixelsLayouts) {
        return new PixelsRemoteFileDesc(pixelsLayouts);
    }

    public List<Layout> getPixelsLayouts() {return pixelsLayouts;}

}
