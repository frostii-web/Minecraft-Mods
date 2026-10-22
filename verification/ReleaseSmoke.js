// Isolated release-artifact verification only. Not included in the mod.
ServerEvents.loaded(event => {
  const Pos=Java.loadClass('net.minecraft.core.BlockPos')
  const RL=Java.loadClass('net.minecraft.resources.ResourceLocation')
  const D=Java.loadClass('net.minecraft.core.Direction')
  const M=Java.loadClass('dev.frostii.irg.block.GeneratorBlockEntity$OutputMode')
  const F=Java.loadClass('dev.frostii.irg.block.GeneratorBlockEntity$FilterMode')
  const List=Java.loadClass('java.util.ArrayList')
  const level=event.server.overworld()
  const id=s=>new RL(s)
  const items=function(){const result=new List();for(let i=0;i<arguments.length;i++)result.add(id(arguments[i]));return result}
  const g=()=>level.getBlockEntity(new Pos(0,100,0))
  const count=(inventory,item)=>{let n=0;for(let i=0;i<inventory.getSlots();i++){let s=inventory.getStackInSlot(i);if(String(s.id)===item)n+=s.getCount()}return n}
  const require=(condition,text)=>{if(!condition)throw new Error('IRG110 FAILED: '+text)}
  event.server.runCommandSilent('forceload add 0 0')
  event.server.scheduleInTicks(10,()=>{
    const gen=g()
    require(gen!=null,'existing 1.0 generator missing')
    require(String(gen.selected())==='gtceu:iron_ore','legacy selection migration')
    if(String(gen.generation(1).resource)==='gtceu:tin_ore'){
      require(!gen.generation(3).enabled,'disabled generation state lost')
      require(gen.generation(4).resource==null&&gen.activeResources().size()===3,'empty rows became phantom filter resources')
      require(String(gen.route(D.EAST).mode)==='DISABLED','saved output mode lost')
      require(String(gen.route(D.NORTH).filter)==='CUSTOM'&&gen.route(D.NORTH).custom.size()===2,'custom resource filter lost')
      require(count(gen.output(),'minecraft:cobblestone')===7,'leftovers lost across restart')
      event.server.scheduleInTicks(25,()=>{
        require(count(gen.output(),'gtceu:iron_ore')>=2&&count(gen.output(),'gtceu:tin_ore')>=1&&count(gen.output(),'gtceu:copper_ore')>=1,'independent generation did not resume')
        console.info('IRG110_RELEASE_RESTART_PASSED: slots, routes, filters, leftovers and generation persisted')
        event.server.runCommandSilent('stop')
      })
      return
    }
    require(count(gen.output(),'gtceu:iron_ore')>=36,'legacy output lost')
    require(gen.configureSlot(0,id('gtceu:iron_ore'),true),'iron config')
    require(gen.configureSlot(1,id('gtceu:tin_ore'),true),'tin config')
    require(gen.configureSlot(2,id('gtceu:copper_ore'),true),'copper config')
    require(gen.configureSlot(3,id('gtceu:iron_ore'),false),'disabled duplicate config')
    require(gen.configureSlot(5,id('gtceu:iron_ore'),true),'active duplicate config')
    gen.output().setStackInSlot(4,Item.of('minecraft:cobblestone',7))
    require(gen.activeResources().size()===3,'active resources not deduplicated')
    event.server.runCommandSilent('setblock 1 100 0 minecraft:chest')
    event.server.runCommandSilent('setblock 0 100 -1 minecraft:chest')
    require(gen.configureRoute(D.NORTH,M.EXTRACT_ONLY,F.CUSTOM,items('gtceu:iron_ore','gtceu:tin_ore')),'combined custom route')
    require(gen.configureRoute(D.EAST,M.PUSH,F.ALL_ACTIVE,items()),'all-active route')
    event.server.scheduleInTicks(35,()=>{
      const chest=level.getBlockEntity(new Pos(1,100,0))
      let iron=0,tin=0,copper=0
      for(let i=0;i<chest.getContainerSize();i++){
        let item=chest.getItem(i)
        require(String(item.id)!=='minecraft:cobblestone','inactive leftovers auto-routed')
        if(String(item.id)==='gtceu:iron_ore')iron+=item.getCount()
        if(String(item.id)==='gtceu:tin_ore')tin+=item.getCount()
        if(String(item.id)==='gtceu:copper_ore')copper+=item.getCount()
      }
      require(iron>=36&&tin>=1&&copper>=1,'release combined PUSH did not transfer real items')
      require(level.getBlockEntity(new Pos(0,100,-1)).isEmpty(),'EXTRACT_ONLY automatically pushed')
      require(count(gen.output(),'minecraft:cobblestone')===7,'inactive leftover deleted')
      require(gen.configureRoute(D.EAST,M.DISABLED,F.ALL_ACTIVE,items()),'disable east')
      console.info('IRG110_RELEASE_MIGRATION_AND_ROUTING_PASSED: existing world, three resources, shared output, preserved leftovers')
      event.server.runCommandSilent('save-all flush')
      event.server.runCommandSilent('stop')
    })
  })
})
