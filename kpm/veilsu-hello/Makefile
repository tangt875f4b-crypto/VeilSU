# SPDX-License-Identifier: GPL-2.0
# Build VeilSU hello KPM as an out-of-tree LKM, same pattern as
# kernel/Makefile (kernelsu.ko): the prepared kernel dir is passed in
# as KDIR, e.g. /opt/ddk/kdir/android17-6.18 in the DDK container.
#
#   make KDIR=/opt/ddk/kdir/android17-6.18 CC=clang
#
# android17-6.18 needs M= + MO= (see kernel/Makefile); other KMIs use
# M= + src=. Keep this in step with kernel/Makefile if it changes.

ifndef KDIR
$(error KDIR is not set — point it at the prepared kernel dir, e.g. /opt/ddk/kdir/android17-6.18)
endif

MDIR := $(realpath $(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
ODIR := $(MDIR)
KMI := $(notdir $(patsubst %/,%,$(KDIR)))

ifeq ($(KMI),android17-6.18)
KBUILD_ARGS := M=$(MDIR) MO=$(ODIR)
else
KBUILD_ARGS := M=$(ODIR) src=$(MDIR)
endif

$(info -- KDIR: $(KDIR))
$(info -- MDIR: $(MDIR))
$(info -- KMI: $(KMI))
$(info -- KBUILD_ARGS: $(KBUILD_ARGS))

.PHONY: all clean

all:
	$(MAKE) -C $(KDIR) $(KBUILD_ARGS) modules -j$(shell nproc)

clean:
	$(MAKE) -C $(KDIR) $(KBUILD_ARGS) clean
