package edu.kit.kastel.vads.compiler.ir

/*
internal class GraphConstructor(private val optimizer: Optimizer, name: String) {
    private val graph: IrGraphOld
    private val currentDef: MutableMap<SymbolName, MutableMap<Block, IrNode>> =
        HashMap<SymbolName, MutableMap<Block, IrNode>>()
    private val incompletePhis: MutableMap<Block, MutableMap<SymbolName, Phi>> =
        HashMap<Block, MutableMap<SymbolName, Phi>>()
    private val currentSideEffect: MutableMap<Block, IrNode> = HashMap<Block, IrNode>()
    private val incompleteSideEffectPhis: MutableMap<Block, Phi> = HashMap<Block, Phi>()
    private val sealedBlocks: MutableSet<Block> = HashSet<Block>()
    private val currentBlock: Block

    init {
        this.graph = IrGraphOld(name)
        this.currentBlock = this.graph.startBlock()
        // the start block never gets any more predecessors
        sealBlock(this.currentBlock)
    }

    fun newStart(): IrNode {
        assert(currentBlock() == this.graph.startBlock()) { "start must be in start block" }
        return StartIrNode(currentBlock())
    }

    fun newAdd(left: IrNode, right: IrNode): IrNode {
        return this.optimizer.transform(AddIrNode(currentBlock(), left, right))
    }

    fun newSub(left: IrNode, right: IrNode): IrNode {
        return this.optimizer.transform(SubIrNode(currentBlock(), left, right))
    }

    fun newMul(left: IrNode, right: IrNode): IrNode {
        return this.optimizer.transform(MulIrNode(currentBlock(), left, right))
    }

    fun newDiv(left: IrNode, right: IrNode): IrNode {
        return this.optimizer.transform(DivIrNode(currentBlock(), left, right, readCurrentSideEffect()))
    }

    fun newMod(left: IrNode, right: IrNode): IrNode {
        return this.optimizer.transform(ModIrNode(currentBlock(), left, right, readCurrentSideEffect()))
    }

    fun newReturn(result: IrNode): IrNode {
        return ReturnIrNode(currentBlock(), readCurrentSideEffect(), result)
    }

    fun newConstInt(value: Int): IrNode {
        // always move const into start block, this allows better deduplication
        // and resultingly in better value numbering
        return this.optimizer.transform(ConstIntIrNode(this.graph.startBlock(), value))
    }

    fun newSideEffectProj(IRNode: IrNode): IrNode {
        return ProjIrNode(currentBlock(), IRNode, SimpleProjectionInfo.SIDE_EFFECT)
    }

    fun newResultProj(IRNode: IrNode): IrNode {
        return ProjIrNode(currentBlock(), IRNode, SimpleProjectionInfo.RESULT)
    }

    fun currentBlock(): Block {
        return this.currentBlock
    }

    fun newPhi(): Phi {
        // don't transform phi directly, it is not ready yet
        return Phi(currentBlock())
    }

    fun graph(): IrGraphOld {
        return this.graph
    }

    fun writeVariable(variable: SymbolName, block: Block, value: IrNode) {
        this.currentDef.computeIfAbsent(variable, Function { `_`: SymbolName? -> HashMap<Block?, IrNode?>() })
            .put(block, value)
    }

    fun readVariable(variable: SymbolName, block: Block): IrNode {
        val IRNode = this.currentDef.getOrDefault(variable, Map.of<Block, IrNode>()).get(block)
        if (IRNode != null) {
            return IRNode
        }
        return readVariableRecursive(variable, block)
    }


    private fun readVariableRecursive(variable: SymbolName, block: Block): IrNode {
        var `val`: IrNode
        if (!this.sealedBlocks.contains(block)) {
            `val` = newPhi()
            this.incompletePhis.computeIfAbsent(block, Function { `_`: Block? -> HashMap<SymbolName?, Phi?>() })
                .put(variable, `val`)
        } else if (block.predecessors().size() == 1) {
            `val` = readVariable(variable, block.predecessors().getFirst().block())
        } else {
            `val` = newPhi()
            writeVariable(variable, block, `val`)
            `val` = addPhiOperands(variable, `val`)
        }
        writeVariable(variable, block, `val`)
        return `val`
    }

    fun addPhiOperands(variable: SymbolName, phi: Phi): IrNode {
        for (pred in phi.block().predecessors()) {
            phi.appendOperand(readVariable(variable, pred.block()))
        }
        return tryRemoveTrivialPhi(phi)
    }

    fun tryRemoveTrivialPhi(phi: Phi): IrNode {
        // TODO: the paper shows how to remove trivial phis.
        // as this is not a problem in Lab 1 and it is just
        // a simplification, we recommend to implement this
        // part yourself.
        return phi
    }

    fun sealBlock(block: Block) {
        for (entry in this.incompletePhis.getOrDefault(block, Map.of<SymbolName, Phi>()).entrySet()) {
            addPhiOperands(entry.getKey(), entry.getValue())
        }
        this.sealedBlocks.add(block)
    }

    fun writeCurrentSideEffect(IRNode: IrNode) {
        writeSideEffect(currentBlock(), IRNode)
    }

    private fun writeSideEffect(block: Block, IRNode: IrNode) {
        this.currentSideEffect.put(block, IRNode)
    }

    fun readCurrentSideEffect(): IrNode {
        return readSideEffect(currentBlock())
    }

    private fun readSideEffect(block: Block): IrNode {
        val node = this.currentSideEffect.get(block)
        if (node != null) {
            return node
        }
        return readSideEffectRecursive(block)
    }

    private fun readSideEffectRecursive(block: Block): IrNode {
        var `val`: IrNode
        if (!this.sealedBlocks.contains(block)) {
            `val` = newPhi()
            val old = this.incompleteSideEffectPhis.put(block, `val`)
            assert(old == null) { "double readSideEffectRecursive for " + block }
        } else if (block.predecessors().size() == 1) {
            `val` = readSideEffect(block.predecessors().getFirst().block())
        } else {
            `val` = newPhi()
            writeSideEffect(block, `val`)
            `val` = addPhiOperands(`val`)
        }
        writeSideEffect(block, `val`)
        return `val`
    }

    fun addPhiOperands(phi: Phi): IrNode {
        for (pred in phi.block().predecessors()) {
            phi.appendOperand(readSideEffect(pred.block()))
        }
        return tryRemoveTrivialPhi(phi)
    }
}
*/
